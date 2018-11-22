package com.github.rvesse.baby.photo.sorter.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.common.ImageMetadata;
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata;
import org.apache.commons.imaging.formats.tiff.TiffField;
import org.apache.commons.imaging.formats.tiff.TiffImageMetadata;
import org.apache.commons.imaging.formats.tiff.constants.ExifTagConstants;
import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.baby.photo.sorter.model.events.Event;

public class Photo {

    private static final Logger LOGGER = LoggerFactory.getLogger(Photo.class);

    //@formatter:off
    private static final DateTimeFormatter EXIF_DATE_FORMAT 
        = new DateTimeFormatterBuilder()
                .appendYear(4, 4)
                .appendLiteral(':')
                .appendMonthOfYear(2)
                .appendLiteral(':')
                .appendDayOfMonth(2)
                .appendLiteral(' ')
                .appendHourOfDay(2)
                .appendLiteral(':')
                .appendMinuteOfHour(2)
                .appendLiteral(':')
                .appendSecondOfMinute(2)
                .toFormatter();
    //@formatter:on

    private final File file;
    private File sourceDirectory;
    private final Path path;
    private boolean loadedCreationDate = false, loadedHash = false;
    private Instant creationDate = null;
    private long sequenceId = 1;
    private Event event = null;
    private String hash;

    public Photo(File file) {
        this.file = file;
        this.path = Paths.get(this.file.toURI());
    }

    public File getFile() {
        return this.file;
    }
    
    public File getSourceDirectory() {
        return this.sourceDirectory != null ? this.sourceDirectory : this.file.getParentFile();
    }
    
    public void setSourceDirectory(File source) {
        this.sourceDirectory = source;
    }

    /**
     * Gets the creation date for the photo
     * <p>
     * Calculated at first request by trying to read the EXIF metadata present
     * in the file (if any)
     * </p>
     * 
     * @return
     */
    public synchronized Instant creationDate() {
        if (loadedCreationDate)
            return creationDate;

        try {
            try {
                ImageMetadata imageMeta = Imaging.getMetadata(this.file);

                if (imageMeta instanceof JpegImageMetadata) {
                    // JPEG Images
                    JpegImageMetadata jpegMeta = (JpegImageMetadata) imageMeta;
                    TiffField dtOriginal = jpegMeta
                            .findEXIFValueWithExactMatch(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL);
                    setCreationDateFromExif(dtOriginal);
                    if (this.loadedCreationDate)
                        return this.creationDate;

                    TiffField dtDigitized = jpegMeta
                            .findEXIFValueWithExactMatch(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED);
                    setCreationDateFromExif(dtDigitized);
                    if (this.loadedCreationDate)
                        return this.creationDate;
                } else if (imageMeta instanceof TiffImageMetadata) {
                    // TIFF Images
                    TiffImageMetadata tiffMeta = (TiffImageMetadata) imageMeta;
                    TiffField dtOriginal = tiffMeta.findField(ExifTagConstants.EXIF_TAG_DATE_TIME_ORIGINAL, true);
                    setCreationDateFromExif(dtOriginal);
                    if (this.loadedCreationDate)
                        return this.creationDate;

                    TiffField dtDigitized = tiffMeta.findField(ExifTagConstants.EXIF_TAG_DATE_TIME_DIGITIZED, true);
                    setCreationDateFromExif(dtDigitized);
                    if (this.loadedCreationDate)
                        return this.creationDate;
                }
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("EXIF medata for photo {} did not contain a creation/digitization date",
                            this.file.getAbsolutePath());
                }
            } catch (ImageReadException e) {
                // Ignore and fallback to using file attributes
                LOGGER.debug("Failed to obtain EXIF metadata for photo {}", this.file.getAbsolutePath());
            }

            // Fall back to file attributes
            BasicFileAttributes attributes = Files.readAttributes(this.path, BasicFileAttributes.class);
            loadedCreationDate = true;
            this.creationDate = new Instant(attributes.creationTime().toMillis());
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Obtained file system creation date for photo {} as {}", this.file.getAbsolutePath(),
                        this.creationDate.toString());
            }
        } catch (IOException e) {
            LOGGER.warn("Photo {} has invalid creation date", this.file.getAbsolutePath());
            loadedCreationDate = true;
        }

        return this.creationDate;
    }

    private void setCreationDateFromExif(TiffField field) throws ImageReadException {
        if (field != null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Obtained EXIF metadata creation date for photo {} as {} from tag {}",
                        this.file.getAbsolutePath(), field.getStringValue(), field.getTagName());
            }
            try {
                this.creationDate = Instant.parse(field.getStringValue(), EXIF_DATE_FORMAT);
                this.loadedCreationDate = true;
            } catch (IllegalArgumentException e) {
                LOGGER.debug("Failed to parse EXIF metadata date for photo {}", this.file.getAbsolutePath());
            }
        }
    }

    /**
     * Gets the file hash for the photo
     * <p>
     * Will be calculated at first request, after that a cached hash will be
     * returned. This obviously assumes that nothing modifies the files contents
     * in the meantime.
     * </p>
     * 
     * @return
     */
    public synchronized String fileHash() {
        if (this.loadedHash)
            return this.hash;

        MessageDigest sha512 = DigestUtils.getSha512Digest();
        DigestUtils digest = new DigestUtils(sha512);
        try {
            this.hash = digest.digestAsHex(this.file);
        } catch (IOException e) {
            LOGGER.warn("Failed to calculate hash for photo {} - {}", this.file.getAbsolutePath(), e.getMessage());
            this.hash = null;
        }

        this.loadedHash = true;
        return this.hash;
    }

    public boolean hasValidCreationDate() {
        return this.creationDate() != null;
    }

    public long ageInDays(Configuration config) {
        Instant i = creationDate();
        if (i != null) {
            Duration d = new Duration(config.dateOfBirth(), i);
            return d.getStandardDays();
        }
        return Long.MIN_VALUE;
    }

    public long ageInWeeks(Configuration config) {
        Instant i = creationDate();
        if (i != null) {
            if (i.isBefore(config.dateOfBirth()))
                return Long.MIN_VALUE;
            Duration d = new Duration(config.dateOfBirth(), i);
            if (d.getStandardDays() < 7) {
                return 0;
            } else {
                return d.getStandardDays() / 7;
            }
        }
        return Long.MIN_VALUE;
    }

    public long ageInMonths(Configuration config) {
        Instant i = creationDate();
        if (i != null) {
            if (i.isBefore(config.dateOfBirth()))
                return Long.MIN_VALUE;
            Period p = new Period(config.dateOfBirth(), i);
            if (p.getYears() > 0) {
                return (p.getYears() * 12) + p.getMonths();
            } else {
                return p.getMonths();
            }
        }
        return Long.MIN_VALUE;
    }

    public long ageInYears(Configuration config) {
        Instant i = creationDate();
        return i != null ? new Period(config.dateOfBirth(), i).getYears() : Long.MIN_VALUE;
    }

    public String getAgeText(Configuration config) {
        Instant i = creationDate();
        if (i == null)
            return "Unknown";

        if (i.isBefore(config.dateOfBirth())) {
            long weeksOfPregnancy = config.weeksOfPregnancy();

            Period p = new Period(i, config.dueDate());
            Duration d = p.toDurationFrom(i);
            return String.format("%d Weeks Pregnant", weeksOfPregnancy - (d.getStandardDays() / 7));
        }

        long days = ageInDays(config);
        if (days != Long.MIN_VALUE && days / 7 < config.weeksThreshold()) {
            return String.format("%d Days", days);
        }
        long weeks = ageInWeeks(config);
        long months = ageInMonths(config);
        if (weeks != Long.MIN_VALUE && weeks >= config.weeksThreshold() && months < config.monthsThreshold()) {
            return String.format("%d Weeks", weeks);
        } else if (months != Long.MIN_VALUE && months >= config.monthsThreshold()
                && (months / 12) < config.yearsThreshold()) {
            return String.format("%d Months", months);
        }
        long years = ageInYears(config);
        if (years != Long.MIN_VALUE) {
            return String.format("%d Years", years);
        } else {
            return "Unknown";
        }
    }

    public String getName(Configuration config) {
        return config.namingPattern().getName(this, config)
                + this.file.getName().substring(this.file.getName().lastIndexOf('.'));
    }

    public long getSequenceId() {
        return this.sequenceId;
    }

    public void setSequenceId(long id) {
        this.sequenceId = id;
    }

    public Event getEvent() {
        return this.event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }
}
