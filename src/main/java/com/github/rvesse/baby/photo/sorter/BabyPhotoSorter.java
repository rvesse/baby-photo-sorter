package com.github.rvesse.baby.photo.sorter;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.airline.annotations.Command;
import com.github.rvesse.airline.annotations.Option;
import com.github.rvesse.airline.annotations.Parser;
import com.github.rvesse.airline.annotations.help.ProseSection;
import com.github.rvesse.airline.annotations.restrictions.AllowedEnumValues;
import com.github.rvesse.airline.annotations.restrictions.Directory;
import com.github.rvesse.airline.annotations.restrictions.MutuallyExclusiveWith;
import com.github.rvesse.airline.annotations.restrictions.NotBlank;
import com.github.rvesse.airline.annotations.restrictions.Required;
import com.github.rvesse.airline.annotations.restrictions.ranges.IntegerRange;
import com.github.rvesse.airline.help.sections.common.CommonSections;
import com.github.rvesse.airline.model.CommandMetadata;
import com.github.rvesse.airline.parser.errors.handlers.CollectAll;
import com.github.rvesse.airline.parser.options.ListValueOptionParser;
import com.github.rvesse.baby.photo.sorter.files.CreationDateComparator;
import com.github.rvesse.baby.photo.sorter.files.ExtensionFilter;
import com.github.rvesse.baby.photo.sorter.files.SubdirectoryFilter;
import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Events;
import com.github.rvesse.baby.photo.sorter.model.Photo;
import com.github.rvesse.baby.photo.sorter.model.events.Event;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingPattern;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingPatternBuilder;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingScheme;

@Command(name = "baby-photo-sorter", description = "Organises, sorts and renames baby photos based on configurable age brackets")
@Parser(flagNegationPrefix = "--no-", errorHandler = CollectAll.class, optionParsers = { ListValueOptionParser.class })
//@formatter:off
@ProseSection(
    title = "Naming Patterns",
    paragraphs = {
        "A naming pattern is a string contained one or more format specifiers that are populated by properties of photos when evaluated.  Any part of the string not recognized as a format specifier is used literally.  The following format specifiers are available:",
        "%n is used to insert the babys name",
        "%a is used to insert the babys age as calculated from the photos date and the babys date of birth",
        "%g is used to insert the photos group name, this is either the name of an event if the photo belongs to a defined event or the name of an age bracket",
        "%d is used to insert the photos creation date and time",
        "%s is used to insert the sequence ID of the photo.  This is a numeric sequence identifier for the photo calculated by sorting the photos in each group into date order and then numbering from one.  The sequence ID will be padded with zeros to a defined length to improve lexicographical sorting of photo names in the resulting folders."
    },
    suggestedOrder = CommonSections.ORDER_DISCUSSION + 1
)
/*@ProseSection(
    title = "Events File",
    paragraphs = {
        "An events file is a simple comma separated text file that contains defined events that you want to use to group photos instead of age brackets.  The format is as follows:",
        "startDate,endDate,name",
        "Where startDate and endDate are given as ISO standard dates - i.e. dd/MM/yyyy HH:mm:ssZ - the time portion is considered optional and if not specified will default to 00:00:00Z for the startDate and 23:59:59Z for the endDate",
        "When grouping photos it will be checked whether"
    },
    suggestedOrder = CommonSections.ORDER_DISCUSSION + 2
)*/
//@formatter:on
public class BabyPhotoSorter {

    private static Logger LOGGER;

    private static final String MAC_THUMBS_FILE = ".DS_Store";
    private static final String WINDOWS_THUMBS_FILE = "Thumbs.db";

    @SuppressWarnings("unused")
    @Inject
    private CommandMetadata metadata;

    @Option(name = { "-s",
            "--source" }, title = "SourceDirectory", description = "Specifies one/more source directories")
    @Directory(mustExist = true, readable = true)
    @Required
    private List<String> sources = new ArrayList<>();

    @Option(name = { "-t",
            "--target" }, title = "TargetDirectory", description = "Specifies the target directory, if not specified photos are organised in-place")
    private String target;

    @Option(name = { "--subfolders",
            "--no-subfolders" }, description = "Specifies whether sorted photos are placed into appropriately named sub-folders")
    private boolean subfolders = true;

    @Option(name = {
            "--naming-scheme" }, title = "NamingScheme", description = "Specifies the desired photo naming scheme from available defaults, can alternatively use --naming-pattern to specify a custom scheme")
    @MutuallyExclusiveWith(tag = "naming")
    @AllowedEnumValues(NamingScheme.class)
    private NamingScheme namingScheme = NamingScheme.NameGroupSequence;

    @Option(name = {
            "--naming-pattern" }, title = "NamingPattern", description = "Specifies a custom photo naming scheme to use, see Naming Patterns for details of available format specifiers.  Alternatively use --naming-scheme to specify one of the default schemes")
    @MutuallyExclusiveWith(tag = "naming")
    private String namingPattern;

    @Option(name = { "-p",
            "--padding" }, title = "SequencePadding", description = "Specifies the number of digits to pad the sequence number to, e.g. with the default of 3 photo sequence 1 would become 001 in the resulting filenames")
    @IntegerRange(min = 1, minInclusive = true)
    private int sequencePadding = 3;

    @Option(name = { "-w",
            "--weeks" }, title = "Weeks", description = "Specifies the number of weeks after which photos will be sorted into age brackets by weeks rather than days (default 1)")
    @IntegerRange(min = 0, minInclusive = true)
    private int weekThreshold = 1;

    @Option(name = { "-m",
            "--months" }, title = "Months", description = "Specifies the number of months after which photos will be sorted into age brackets by months rather than weeks/days (default 3)")
    @IntegerRange(min = 0, minInclusive = true)
    private int monthThreshold = 3;

    @Option(name = { "-y",
            "--years" }, title = "Years", description = "Specifies the number of years after which photos will be sorted into age brackets by years rather than months/weeks/days (default 1)")
    @IntegerRange(min = 1, minInclusive = true)
    private int yearThreshold = 1;

    @Option(name = { "-d", "--dob",
            "--date-of-birth" }, title = "DateOfBirth", description = "Specifies the date of birth of the baby, required in order to calculate ages")
    @Required
    private String dob;

    @Option(name = {
            "--due-date" }, title = "DueDate", description = "Specifies the due date of the baby, if not specified will be assumed to be the same as the date of birth.  Specifying this separately allows us to more accurately calculate number of weeks pregnant for photos taken prior to the date of birth.")
    private String dueDate;

    @Option(name = { "-n",
            "--name" }, title = "BabyName", description = "Specifies the name of the baby used in renaming the photos")
    @Required
    @NotBlank
    private String name;

    @Option(name = {
            "--events" }, title = "EventsFile", description = "Specifies an events file that defines special events that are used to group photos")
    @com.github.rvesse.airline.annotations.restrictions.File(mustExist = true, readable = true)
    private String eventsFile;

    @Option(name = { "-e",
            "--extensions" }, title = "Extensions", description = "Specifies the file extensions that are treated as photos, if not specified then .jpg and .jpeg are the only file extensions used by default")
    private List<String> extensions = new ArrayList<>();

    @Option(name = { "--verbose" }, description = "Enables verbose logging")
    private boolean verbose = false;

    @Option(name = { "--preserve" }, description = "Specifies that original photos should be preserved")
    @MutuallyExclusiveWith(tag = "preserveOrReorg")
    private boolean preserveOriginals = false;

    @Option(name = {
            "--dry-run" }, description = "Specifies that a dry run should be done i.e. report what would have happened but don't actually do it.  When set also enabled verbose logging i.e. --dry-run implies --verbose")
    private boolean dryRun = false;

    @Option(name = {
            "--reorg" }, description = "Specifies that photos sorted from previous runs should be reorganised.  This option only makes sense if using --subfolders or a target directory is used.  It causes sub-folders of the target directories (or the source directories if no explicit target directory is given) to be rescanned and reorganised.  This can be useful if you want to change your organisation criteria or have imported new photos that overlap with your previously organised photos.")
    @MutuallyExclusiveWith(tag = "preserveOrReorg")
    private boolean reorg = false;

    @Option(name = {
            "--ignore" }, description = "Specifies that one/more directories should be excluded from scanning.  This may be useful when using --reorg if you have some sub-folders organised by hand that you don't want modified.")
    @Directory(mustExist = false, writable = false)
    private List<String> ignore = new ArrayList<>();

    @Option(name = {
            "--de-duplicate" }, description = "Specifies that duplicate photos should be detected.  By default duplicates are deleted unless the --keep-duplicates option is used.  Note that you will be prompted before any deletes happen so you can choose to proceed with deletes or abort as desired, if you want to allow deletes regardless please use the --allow-deletes option.")
    private boolean deduplicate = false;

    @Option(name = {
            "--keep-duplicates" }, description = "Specifies that duplicate photos should be kept, this only has an effect when --de-duplicate is used.")
    private boolean keepDuplicates = false;

    @Option(name = {
            "--allow-deletes" }, description = "Specifies that deletion of duplicate photos should be permitted, this only has an effect when --de-duplicate or --clean-empty-dirs is used.")
    private boolean allowDeletes = false;

    @Option(name = {
            "--clean-empty-dirs" }, description = "Specifies that any resulting empty directories after organisation should be deleted")
    private boolean cleanEmptyDirs = false;

    public void run() {
        // Dry Run implies Verbose
        if (this.dryRun)
            this.verbose = true;

        // Set up Log4j
        // If Verbose set log level to DEBUG
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(verbose ? Level.DEBUG : Level.INFO);
        builder.setConfigurationName("BabyPhotoSorter");
        AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE").addAttribute("target",
                ConsoleAppender.Target.SYSTEM_OUT);
        appenderBuilder
                .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable"));
        builder.add(appenderBuilder);
        builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG).add(builder.newAppenderRef("Stdout"))
                .addAttribute("additivity", false));
        builder.add(builder.newRootLogger(verbose ? Level.DEBUG : Level.INFO).add(builder.newAppenderRef("Stdout")));
        LoggerContext ctx = Configurator.initialize(builder.build());
        ctx.updateLoggers();
        LOGGER = LoggerFactory.getLogger(BabyPhotoSorter.class);

        if (this.reorg && (!this.subfolders && this.target == null)) {
            LOGGER.warn(
                    "Using --reorg is unnecessary when not using --subfolders/--target, source directories will already be rescanned and reorganised");
        }

        // Create a configuration
        //@formatter:off
        DateTimeFormatter dateFormat 
            = new DateTimeFormatterBuilder()
                    .appendDayOfMonth(1)
                    .appendLiteral('/')
                    .appendMonthOfYear(1)
                    .appendLiteral('/')
                    .appendYear(4, 4)
                    .appendLiteral(' ')
                    .appendHourOfDay(2)
                    .appendLiteral(':')
                    .appendMinuteOfHour(2)
                    .appendLiteral(':')
                    .appendSecondOfMinute(2)
                    .appendTimeZoneOffset("Z", "Z", true, 2, 2)
                    .toFormatter();
        //@formatter:on
        Instant dob = Instant.parse(this.dob + " 00:00:00Z", dateFormat);
        Instant dueDate = this.dueDate != null ? Instant.parse(this.dueDate + " 00:00:00Z", dateFormat) : dob;
        if (this.extensions.size() == 0) {
            this.extensions.add(".jpg");
            this.extensions.add(".jpeg");
        }
        NamingPattern namePattern;
        if (this.namingPattern != null) {
            namePattern = NamingPatternBuilder.parse(this.namingPattern);
        } else {
            namePattern = this.namingScheme.getPattern();
        }
        Events events;
        if (this.eventsFile != null) {
            events = Events.parse(new File(this.eventsFile), dateFormat);
        } else {
            events = new Events();
        }
        Set<String> ignoredDirs = new HashSet<>();
        if (this.ignore != null) {
            for (String dir : this.ignore) {
                ignoredDirs.add(new File(dir).getAbsolutePath());
            }
        }
        // TODO Support configurable DOB format
        Configuration config = new Configuration(dob, dueDate, this.name, this.weekThreshold, this.monthThreshold,
                this.yearThreshold, events, extensions, this.sequencePadding, namePattern);

        // Start by discovering photos
        List<Photo> photos = discoverPhotos(config, ignoredDirs);

        // Sort files by creation date
        photos.sort(new CreationDateComparator());

        // Next bucket into groups
        Map<String, List<Photo>> groups = groupPhotos(config, dateFormat, photos);

        // Do de-duplication at this stage
        if (this.deduplicate) {
            deduplicatePhotos(config, groups);
        }

        // Create directories if appropriate
        prepareGroups(config, groups);

        // Reorganise photos
        organisePhotos(config, groups);

        LOGGER.info("Discovered {} photos in {} source directories", photos.size(), this.sources.size());

        if (this.cleanEmptyDirs) {
            LOGGER.info("Looking for empty directories to clean up...");

            int cleaned = cleanEmptyDirectories(config, ignoredDirs);

            LOGGER.info("Cleaned {} empty directories", cleaned);
        }
    }

    private int cleanEmptyDirectories(Configuration config, Collection<String> ignoredDirs) {
        int cleaned = 0;

        SubdirectoryFilter subdirFilter = new SubdirectoryFilter();
        for (String source : this.sources) {
            if (source == null || source.length() == 0) {
                continue;
            }

            File sourceDir = new File(source);
            if (!sourceDir.isDirectory()) {
                LOGGER.error("Source {} is not a directory", sourceDir.getAbsolutePath());
            }
            if (ignoredDirs.contains(sourceDir.getAbsolutePath())) {
                LOGGER.warn("Ignoring directory {} as requested", sourceDir.getAbsolutePath());
                continue;
            }

            LOGGER.info("Looking for empty directories in source directory {}", sourceDir.getAbsolutePath());

            // Clean any sub-directories found
            for (File subDir : sourceDir.listFiles(subdirFilter)) {
                cleaned += cleanEmptyDirectories(subDir, ignoredDirs);
            }
        }

        // Clean target directories if using them
        if (this.target != null) {
            File targetDir = new File(target);
            if (!targetDir.isDirectory()) {
                LOGGER.error("Target {} is not a directory", targetDir.getAbsolutePath());
                return cleaned;
            }
            if (ignoredDirs.contains(targetDir.getAbsolutePath())) {
                LOGGER.warn("Ignoring target directory {} as requested", targetDir.getAbsolutePath());
                return cleaned;
            }

            // Clean any sub-directories found
            for (File subDir : targetDir.listFiles(subdirFilter)) {
                cleaned += cleanEmptyDirectories(subDir, ignoredDirs);
            }
        }

        return cleaned;
    }

    private int cleanEmptyDirectories(File dir, Collection<String> ignoredDirs) {
        if (ignoredDirs.contains(dir.getAbsolutePath()))
            return 0;

        int files = dir.listFiles().length;
        if (files == 0) {
            LOGGER.info("Removing empty directory {}", dir.getAbsolutePath());
            if (!this.dryRun) {
                if (!this.allowDeletes) {
                    confirmDeletions("empty directories");
                }
                if (!dir.delete()) {
                    LOGGER.warn("Failed to delete empty directory {}", dir.getAbsolutePath());
                }
                LOGGER.info("Deleted empty directory {}", dir.getAbsolutePath());
            }
            return 1;
        } else if (files == 1) {
            // Is is just the system Thumbnail database file present?
            // If so clean that up and then delete the directory as well
            File maybeThumbsFile = dir.listFiles()[0];
            if (StringUtils.equals(maybeThumbsFile.getName(), MAC_THUMBS_FILE)
                    || StringUtils.equals(maybeThumbsFile.getName(), WINDOWS_THUMBS_FILE)) {
                LOGGER.info("Removing empty directory {}", dir.getAbsolutePath());
                if (!this.dryRun) {
                    if (!this.allowDeletes) {
                        confirmDeletions("empty directories");
                    }
                    if (!maybeThumbsFile.delete() && !dir.delete()) {
                        LOGGER.warn("Failed to delete empty directory {}", dir.getAbsolutePath());
                    }
                    LOGGER.info("Deleted empty directory {}", dir.getAbsolutePath());
                }
                return 1;
            }
        }

        int cleaned = 0;
        for (File subDir : dir.listFiles(new SubdirectoryFilter())) {
            cleaned += cleanEmptyDirectories(subDir, ignoredDirs);
        }

        if (cleaned > 0 && dir.exists() && !this.dryRun) {
            // If we've cleaned some sub-directories then may be able to clean
            // ourselves now
            return cleaned + cleanEmptyDirectories(dir, ignoredDirs);
        }

        return cleaned;
    }

    private void organisePhotos(Configuration config, Map<String, List<Photo>> groups) {
        for (String groupName : groups.keySet()) {
            List<Photo> ps = groups.get(groupName);

            Set<String> newLocations = new HashSet<>();
            Set<String> oldLocations = new HashSet<>();
            Set<String> conflicts = new HashSet<>();
            int noOps = 0;

            // Calculate Targets
            for (Photo p : ps) {
                // Have to calculate target directory each time in case we are
                // organising in-place and have multiple source directories
                File targetDir = this.target != null ? new File(this.target) : p.getSourceDirectory();
                if (this.subfolders) {
                    targetDir = new File(targetDir, groupName);
                }

                // If not using an explicit target directory we won't have tried
                // to create the directory yet because the target directory
                // depends on the source directory. In this case we need to try
                // and create the directory here.
                if (!targetDir.exists() || !targetDir.isDirectory()) {
                    if (!this.dryRun) {
                        if (!targetDir.mkdirs()) {
                            LOGGER.error("Failed to create required target directory {}", targetDir.getAbsolutePath());
                            System.exit(1);
                        }
                    } else {
                        LOGGER.debug("Ensuring required target directory {} exists", targetDir.getAbsolutePath());
                    }
                }

                // Get the new name for the photo
                String newName = p.getName(config);
                p.setTargetFile(new File(targetDir, newName));

                if (p.isNoOp()) {
                    noOps++;
                }

                // Track for potential conflicting moves
                oldLocations.add(p.getFile().getAbsolutePath());
                if (!newLocations.add(p.getTargetFile().getAbsolutePath())) {
                    // We shouldn't ever calculate multiple photos targeted at
                    // the same place but we should still check for this just in
                    // case
                    LOGGER.warn("Multiple photos targeted at file {}", p.getTargetFile().getAbsolutePath());
                    conflicts.add(p.getTargetFile().getAbsolutePath());
                }
            }

            if (noOps == ps.size()) {
                LOGGER.info("All photos in group {} are already in correct location, no reorganisation to do",
                        groupName);
                continue;
            }

            // Determine move conflicts
            for (String location : newLocations) {
                if (oldLocations.contains(location)) {
                    // Move Conflict, we're moving this file to a location that
                    // is already in use so this could cause data loss!!!
                    conflicts.add(location);
                }
            }

            if (conflicts.size() > 0) {
                LOGGER.warn("{} {} conflicts detected for group {}", conflicts.size(),
                        this.preserveOriginals ? "copy" : "move", groupName);

                // Handle conflicts by changing the source location of the file
                // to a temporary location
                for (Photo p : ps) {
                    File tempFile = null;
                    try {
                        // Create a temporary file location
                        // This creates a zero byte file which we should
                        // immediately delete
                        tempFile = File.createTempFile("photo", p.getExtension(), p.getTargetFile().getParentFile());
                        tempFile.delete();
                        LOGGER.debug("Renaming Photo {} temporarily to {} to avoid {} conflicts",
                                p.getFile().getAbsolutePath(), tempFile.getAbsolutePath(),
                                this.preserveOriginals ? "copy" : "move");

                        // Copy/Move there as appropriate
                        if (this.preserveOriginals) {
                            if (!this.dryRun) {
                                Files.copy(p.getFile().toPath(), tempFile.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
                            }
                        } else {
                            if (!this.dryRun) {
                                Files.move(p.getFile().toPath(), tempFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
                            }
                        }

                        // Update source file accordingly
                        p.setFile(tempFile);
                    } catch (IOException e) {
                        LOGGER.error("Failed to temporarily rename photo {} to {} - {}", p.getFile().getAbsolutePath(),
                                tempFile, e.getMessage());
                        System.exit(1);
                    }
                }
            }

            // Do the actual copies/moves, we've resolved possible conflicts by
            // copying/moving the sources to a temporary location at this point
            for (Photo p : ps) {
                // Check whether there is actually anything to do
                // i.e. if the photo is already in the correct place and has the
                // correct name just skip it
                if (p.isNoOp()) {
                    // Source and Target Filename are also the same
                    // Therefore nothing to do
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Photo {} is already sorted into the correct location",
                                p.getFile().getAbsolutePath());
                    }

                    // Skip this photo
                    oldLocations.remove(p.getFile().getAbsolutePath());
                    newLocations.remove(p.getTargetFile().getAbsolutePath());
                    continue;
                }

                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("{} photo {} to folder {} as {}", this.preserveOriginals ? "Copying" : "Moving",
                            p.getFile().getAbsolutePath(), p.getTargetFile().getParentFile().getAbsolutePath(),
                            p.getTargetFile().getName());

                if (p.getTargetFile().exists()) {
                    LOGGER.error(
                            "Unable to {} photo {} to target file {} as a file of that name already exists, refusing to overwrite an existing file!",
                            this.preserveOriginals ? "copy" : "move", p.getFile().getAbsolutePath(),
                            p.getTargetFile().getAbsolutePath());
                    System.exit(1);
                }

                // Perform actual move/copy
                if (this.preserveOriginals) {
                    try {
                        if (!this.dryRun)
                            Files.copy(p.getFile().toPath(), p.getTargetFile().toPath(),
                                    StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (IOException e) {
                        LOGGER.error("Failed to copy photo {} to directory {} - {}", p.getFile().getAbsolutePath(),
                                p.getTargetFile().getParentFile().getAbsolutePath(), e.getMessage());
                        System.exit(1);
                    }
                } else {
                    try {
                        if (!this.dryRun)
                            Files.move(p.getFile().toPath(), p.getTargetFile().toPath(),
                                    StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        LOGGER.error("Failed to move photo {} to directory {} - {}", p.getFile().getAbsolutePath(),
                                p.getTargetFile().getParentFile().getAbsolutePath(), e.getMessage());
                        System.exit(1);
                    }
                }

                oldLocations.remove(p.getFile().getAbsolutePath());
                newLocations.remove(p.getTargetFile().getAbsolutePath());
            }

            // Verify that all the expected files exist
            if (!this.dryRun) {
                for (Photo p : ps) {
                    if (!p.getTargetFile().exists()) {
                        LOGGER.error("FATAL: Expected Photo {} was not found, data loss may have occurred!",
                                p.getTargetFile().getAbsolutePath());
                        System.exit(1);
                    }
                }
            }
        }

    }

    private void prepareGroups(Configuration config, Map<String, List<Photo>> groups) {
        File targetDir = this.target != null ? new File(this.target) : null;
        for (String bracket : groups.keySet()) {
            LOGGER.info("Group {} contains {} photos", bracket, groups.get(bracket).size());

            // Create the required sub-folder
            File bracketDir = null;
            if (this.subfolders && targetDir != null) {
                bracketDir = new File(targetDir, bracket);
                if (bracketDir.exists() && bracketDir.isDirectory())
                    continue;
                if (!this.dryRun) {
                    if (!bracketDir.mkdirs()) {
                        LOGGER.error("Failed to create target directory {}", bracketDir.getAbsolutePath());
                        System.exit(1);
                    }
                } else {
                    LOGGER.debug("Ensuring target directory {} exists", bracketDir.getAbsolutePath());
                }
            }

            // Create sequence numbering
            long id = 0;
            // Determine the initial sequence number based on existing organised
            // photos unless we're reorganising
            if (bracketDir != null && !this.reorg) {
                id += bracketDir.list(new ExtensionFilter(config)).length;
                if (id > 0)
                    LOGGER.debug("Target directory {} already has {} photos sorted into it", bracketDir, id);
            }
            for (Photo p : groups.get(bracket)) {
                p.setSequenceId(++id);
            }

        }
    }

    private void deduplicatePhotos(Configuration config, Map<String, List<Photo>> groups) {

        for (Entry<String, List<Photo>> group : groups.entrySet()) {
            Map<String, List<Photo>> photosByHash = new HashMap<>();

            LOGGER.debug("Checking for duplicates in group {}", group.getKey());
            for (Photo p : group.getValue()) {
                String hash = p.fileHash();
                if (!photosByHash.containsKey(hash)) {
                    photosByHash.put(hash, new ArrayList<>());
                }
                photosByHash.get(hash).add(p);
            }

            if (photosByHash.keySet().size() < group.getValue().size()) {
                // Fewer hashes than photos so some duplicates
                for (Entry<String, List<Photo>> hashGroup : photosByHash.entrySet()) {
                    List<Photo> ps = hashGroup.getValue();
                    if (ps.size() <= 1)
                        continue;

                    // Report the photos with the same hash
                    LOGGER.warn("{} Photos have the same file hash {}:", ps.size(), hashGroup.getKey());
                    for (Photo p : hashGroup.getValue()) {
                        LOGGER.warn("  {}", p.getFile().getAbsolutePath());
                    }

                    // Delete the duplicates unless a dry run or keeping
                    // duplicates
                    if (!this.dryRun && !this.keepDuplicates) {
                        while (ps.size() > 1) {
                            Photo toDelete = ps.get(1);

                            if (!this.allowDeletes) {
                                confirmDeletions("duplicate photos");
                            }

                            if (!toDelete.getFile().delete()) {
                                LOGGER.error("Failed to delete duplicate file {}",
                                        toDelete.getFile().getAbsolutePath());
                                System.exit(1);
                            }
                            ps.remove(1);

                            // Need to also remove the deleted photo from the
                            // source group as otherwise subsequent steps may
                            // incorrectly attempt to process the now deleted
                            // photo
                            group.getValue().remove(toDelete);
                        }
                    }
                }
            } else {
                LOGGER.debug("No duplicates found in group {}", group.getKey());
            }
        }
    }

    private void confirmDeletions(String items) {
        System.out.print(String.format("Are you sure you wish to delete %s? [y/n]: ", items));
        try {
            int deletePromptResponse = System.in.read();
            switch (deletePromptResponse) {
            case 'Y':
            case 'y':
                this.allowDeletes = true;
                break;
            default:
                LOGGER.warn("User refused to allow deletion of {}, sorting aborted!", items);
                System.exit(1);
            }
        } catch (IOException e) {
            LOGGER.error("Bad response to delete confirmation prompt - {}", e.getMessage());
        }
    }

    private Map<String, List<Photo>> groupPhotos(Configuration config, DateTimeFormatter dateFormat,
            List<Photo> photos) {
        Map<String, List<Photo>> groups = new LinkedHashMap<>();
        for (Photo p : photos) {
            String group;
            Event e = config.events().inEvent(p);
            if (e != null) {
                group = e.name();
                p.setEvent(e);
                e.increment();
            } else {
                group = p.getAgeText(config);
            }

            if (LOGGER.isDebugEnabled())
                LOGGER.debug("Photo {} has creation date {} and is in group {}", p.getFile().getAbsolutePath(),
                        p.creationDate().toString(dateFormat), group);

            if (!groups.containsKey(group)) {
                groups.put(group, new ArrayList<>());
            }
            groups.get(group).add(p);
        }

        // Issue warnings for any events that don't have any photos in them
        for (Event event : config.events().getEvents()) {
            if (event.size() == 0) {
                LOGGER.warn(
                        "Event {} with start date {} and end date {} did not match any photos - are you sure you defined the date range correctly?",
                        event.name(), event.start(), event.end());
            }
        }

        LOGGER.info("Sorted {} photos into {} groups", photos.size(), groups.keySet().size());
        return groups;
    }

    private List<Photo> discoverPhotos(Configuration config, Collection<String> ignoredDirs) {
        List<Photo> photos = new ArrayList<>();
        ExtensionFilter extFilter = new ExtensionFilter(config);
        for (String source : this.sources) {
            if (source == null || source.length() == 0) {
                continue;
            }

            File sourceDir = new File(source);
            if (!sourceDir.isDirectory()) {
                LOGGER.error("Source {} is not a directory", source);
            }
            if (ignoredDirs.contains(sourceDir.getAbsolutePath())) {
                LOGGER.warn("Ignoring directory {} as requested", sourceDir.getAbsolutePath());
                continue;
            }

            LOGGER.info("Scanning source directory {}", sourceDir.getAbsolutePath());

            int found = scanDirectory(sourceDir, extFilter, photos, sourceDir);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Source directory {} contained {} photos", sourceDir.getAbsolutePath(), found);
            }

            // If reorganising and no explicit target also scan sub-directories
            // of the source directory (if using sub-folders)
            if (this.reorg && this.target == null && this.subfolders) {
                found += scanSubDirectories(ignoredDirs, photos, extFilter, sourceDir, false, sourceDir);
            }
        }

        // When reorganising scan the target directory (if it exists) and any
        // sub-directories thereof
        if (this.reorg && this.target != null) {
            File targetDir = new File(this.target);

            if (ignoredDirs.contains(targetDir.getAbsolutePath())) {
                LOGGER.warn("Ignoring target directory {} as requested, reorganisation may be ineffectual as a result",
                        targetDir.getAbsolutePath());
            } else {
                if (targetDir.exists() && targetDir.isDirectory()) {
                    LOGGER.info("Scanning target directory {} for reorganisation", targetDir.getAbsolutePath());
                    int found = scanDirectory(targetDir, extFilter, photos, targetDir);
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("Target directory {} contained {} photos", targetDir.getAbsolutePath(), found);
                    }

                    if (this.subfolders) {
                        found += scanSubDirectories(ignoredDirs, photos, extFilter, targetDir, true, targetDir);
                    }
                }
            }
        }

        return photos;
    }

    private int scanSubDirectories(Collection<String> ignoredDirs, List<Photo> photos, ExtensionFilter extFilter,
            File sourceDir, boolean wasTargetDir, File originalSourceDirectory) {
        int found = 0;
        for (File subdir : sourceDir.listFiles(new SubdirectoryFilter())) {
            if (ignoredDirs.contains(subdir.getAbsolutePath())) {
                if (wasTargetDir) {
                    LOGGER.warn(
                            "Ignoring target sub-directory {} as requested, reorganisation may be ineffectual as a result",
                            subdir.getAbsolutePath());
                } else {
                    LOGGER.warn("Ignoring sub-directory {} as requested", subdir.getAbsolutePath());
                }
                continue;
            }

            LOGGER.info("Scanning sub-directory {} for reorganisation", subdir.getAbsolutePath());
            found += scanDirectory(subdir, extFilter, photos, originalSourceDirectory);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Sub-directory {} contained {} photos", subdir.getAbsolutePath(), found);
            }

            found += scanSubDirectories(ignoredDirs, photos, extFilter, subdir, wasTargetDir, originalSourceDirectory);
        }
        return found;
    }

    private int scanDirectory(File sourceDir, FilenameFilter filter, List<Photo> photos, File originalSourceDirectory) {
        int found = 0;
        for (File f : sourceDir.listFiles(filter)) {
            // Ignore and delete zero-length files
            if (f.length() == 0) {
                f.delete();
                continue;
            }

            Photo p = new Photo(f);
            p.setSourceDirectory(originalSourceDirectory);
            photos.add(p);
            found++;
        }
        return found;
    }

}
