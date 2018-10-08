package com.github.rvesse.baby.photo.sorter;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
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
import com.github.rvesse.airline.annotations.restrictions.AllowedEnumValues;
import com.github.rvesse.airline.annotations.restrictions.Directory;
import com.github.rvesse.airline.annotations.restrictions.MutuallyExclusiveWith;
import com.github.rvesse.airline.annotations.restrictions.Required;
import com.github.rvesse.airline.annotations.restrictions.ranges.IntegerRange;
import com.github.rvesse.airline.model.CommandMetadata;
import com.github.rvesse.airline.parser.errors.handlers.CollectAll;
import com.github.rvesse.baby.photo.sorter.files.CreationDateComparator;
import com.github.rvesse.baby.photo.sorter.files.ExtensionFilter;
import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;
import com.github.rvesse.baby.photo.sorter.model.naming.NamingScheme;

@Command(name = "baby-photo-sorter", description = "Organises, sorts and renames baby photos based on configurable age brackets")
@Parser(flagNegationPrefix = "--no-", errorHandler = CollectAll.class)
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
    private NamingScheme namingScheme = NamingScheme.NameAgeSequence;

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

    @Option(name = { "-e",
            "--extension" }, title = "Extensions", description = "Specifies the file extensions that are treated as photos, if not specified then .jpg and .jpeg are the only file extensions used by default")
    public List<String> extensions = new ArrayList<>();
    
    @Option(name = { "--verbose" }, description = "Enables verbose logging")
    public boolean verbose = false;

    public void run() {
        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.setStatusLevel(verbose ? Level.DEBUG : Level.INFO);
        builder.setConfigurationName("BabyPhotoSorter");
        AppenderComponentBuilder appenderBuilder = builder.newAppender("Stdout", "CONSOLE").addAttribute("target",
            ConsoleAppender.Target.SYSTEM_OUT);
        appenderBuilder.add(builder.newLayout("PatternLayout")
            .addAttribute("pattern", "%d [%t] %-5level: %msg%n%throwable"));
        builder.add(appenderBuilder);
        builder.add(builder.newLogger("org.apache.logging.log4j", Level.DEBUG)
            .add(builder.newAppenderRef("Stdout")).addAttribute("additivity", false));
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
        // TODO Support configurable DOB format
        Configuration config = new Configuration(dob, this.weekThreshold, this.monthThreshold, this.yearThreshold,
                extensions);

        // Start by discovering photos
        List<Photo> photos = new ArrayList<>();
        ExtensionFilter extFilter = new ExtensionFilter(config);
        for (String source : this.sources) {
            if (source == null || source.length() == 0) {
                continue;
            }

            File sourceDir = new File(source);
            if (!sourceDir.isDirectory()) {
                LOGGER.error(String.format("Source %s is not a directory", source));
            }
            LOGGER.info(String.format("Scanning source directory %s", sourceDir.getAbsolutePath()));

            for (File f : sourceDir.listFiles(extFilter)) {
                photos.add(new Photo(f));
            }
        }
        
        // Sort files by creation date
        photos.sort(new CreationDateComparator());

        // Next bucket into age brackets
        Map<String, List<Photo>> ageBrackets = new LinkedHashMap<>();
        for (Photo p : photos) {
            String ageText = p.getAgeText(config);
            LOGGER.debug(String.format("Photo %s has creation date %s and is in age bracket %s",
                    p.getFile().getAbsolutePath(), p.creationDate().toString(dateFormat), ageText));

            if (!ageBrackets.containsKey(ageText)) {
                ageBrackets.put(ageText, new ArrayList<>());
            }
            ageBrackets.get(ageText).add(p);
        }
        LOGGER.info(
                String.format("Sorted %d photos into %d age brackets", photos.size(), ageBrackets.keySet().size()));
        for (String bracket : ageBrackets.keySet()) {
            LOGGER.info(
                    String.format("Age Bracket %s contains %d photos", bracket, ageBrackets.get(bracket).size()));
        }

        LOGGER.info(
                String.format("Discovered %d photos in %d source directories", photos.size(), this.sources.size()));
    }

}
