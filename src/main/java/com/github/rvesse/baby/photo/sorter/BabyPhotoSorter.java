package com.github.rvesse.baby.photo.sorter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

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
import com.github.rvesse.baby.photo.sorter.files.CreationDateComparator;
import com.github.rvesse.baby.photo.sorter.files.ExtensionFilter;
import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Events;
import com.github.rvesse.baby.photo.sorter.model.Photo;
import com.github.rvesse.baby.photo.sorter.model.events.Event;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingPattern;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingPatternBuilder;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingScheme;

@Command(name = "baby-photo-sorter", description = "Organises, sorts and renames baby photos based on configurable age brackets")
@Parser(flagNegationPrefix = "--no-", errorHandler = CollectAll.class)
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
            "--extension" }, title = "Extensions", description = "Specifies the file extensions that are treated as photos, if not specified then .jpg and .jpeg are the only file extensions used by default")
    private List<String> extensions = new ArrayList<>();

    @Option(name = { "--verbose" }, description = "Enables verbose logging")
    private boolean verbose = false;

    @Option(name = { "--preserve" }, description = "Specifies that original photos should be preserved")
    private boolean preserveOriginals = false;

    @Option(name = {
            "--dry-run" }, description = "Specifies that a dry run should be done i.e. report what would have happened but don't actually do it.  When set also enabled verbose logging i.e. --dry-run implies --verbose")
    private boolean dryRun = false;

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
        // TODO Support configurable DOB format
        Configuration config = new Configuration(dob, this.name, this.weekThreshold, this.monthThreshold,
                this.yearThreshold, events, extensions, this.sequencePadding, namePattern);

        // Start by discovering photos
        List<Photo> photos = discoverPhotos(config);

        // Sort files by creation date
        photos.sort(new CreationDateComparator());

        // Next bucket into groups
        Map<String, List<Photo>> groups = groupPhotos(dateFormat, config, photos);

        // Create directories if appropriate
        prepareGroups(groups);

        // Reorganise photos
        organisePhotos(config, groups);

        LOGGER.info("Discovered {} photos in {} source directories", photos.size(), this.sources.size());
    }

    private void organisePhotos(Configuration config, Map<String, List<Photo>> groups) {
        for (String groupName : groups.keySet()) {
            List<Photo> ps = groups.get(groupName);

            for (Photo p : ps) {
                // Have to calculate target directory each time in case we are
                // organising in-place and have multiple source directories
                File targetDir = this.target != null ? new File(this.target) : p.getFile().getParentFile();
                if (this.subfolders) {
                    targetDir = new File(targetDir, groupName);
                }

                
                String newName = p.getName(config);
                if (LOGGER.isDebugEnabled())
                    LOGGER.debug("{} photo {} to folder {} as {}", this.preserveOriginals ? "Copying" : "Moving",
                            p.getFile().getAbsolutePath(), targetDir.getAbsolutePath(), newName);

                // Perform actual move/copy
                if (this.preserveOriginals) {
                    try {
                        if (!this.dryRun)
                            Files.copy(p.getFile().toPath(), new File(targetDir, newName).toPath(),
                                    StandardCopyOption.COPY_ATTRIBUTES);
                    } catch (IOException e) {
                        LOGGER.error("Failed to copy photo {} to directory {}", p.getFile().getAbsolutePath(),
                                targetDir.getAbsolutePath());
                    }
                } else {
                    try {
                        if (!this.dryRun)
                            Files.move(p.getFile().toPath(), new File(targetDir, newName).toPath(),
                                    StandardCopyOption.ATOMIC_MOVE);
                    } catch (IOException e) {
                        LOGGER.error("Failed to move photo {} to directory {}", p.getFile().getAbsolutePath(),
                                targetDir.getAbsolutePath());
                    }
                }
            }
        }

    }

    private void prepareGroups(Map<String, List<Photo>> groups) {
        File targetDir = this.target != null ? new File(this.target) : null;
        for (String bracket : groups.keySet()) {
            LOGGER.info("Group {} contains {} photos", bracket, groups.get(bracket).size());

            // Create sequence numbering
            long id = 0;
            for (Photo p : groups.get(bracket)) {
                p.setSequenceId(++id);
            }

            // Create the required subfolder
            if (this.subfolders && targetDir != null) {
                File bracketDir = new File(targetDir, bracket);
                if (bracketDir.exists() && bracketDir.isDirectory())
                    continue;
                if (!bracketDir.mkdirs()) {
                    LOGGER.error("Failed to create target directory {}", bracketDir.getAbsolutePath());
                    System.exit(1);
                }
            }
        }
    }

    private Map<String, List<Photo>> groupPhotos(DateTimeFormatter dateFormat, Configuration config,
            List<Photo> photos) {
        Map<String, List<Photo>> groups = new LinkedHashMap<>();
        for (Photo p : photos) {
            String group;
            Event e = config.events().inEvent(p);
            if (e != null) {
                group = e.name();
                p.setEvent(e);
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
        LOGGER.info("Sorted {} photos into {} groups", photos.size(), groups.keySet().size());
        return groups;
    }

    private List<Photo> discoverPhotos(Configuration config) {
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
            LOGGER.info("Scanning source directory {}", sourceDir.getAbsolutePath());

            for (File f : sourceDir.listFiles(extFilter)) {
                photos.add(new Photo(f));
            }
        }
        return photos;
    }

}
