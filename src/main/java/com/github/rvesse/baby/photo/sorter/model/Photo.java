package com.github.rvesse.baby.photo.sorter.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.baby.photo.sorter.model.events.Event;

public class Photo {

    private static final Logger LOGGER = LoggerFactory.getLogger(Photo.class);

    private final File file;
    private final Path path;
    private boolean loadedCreationDate = false;
    private Instant creationDate = null;
    private long sequenceId = 1;
    private Event event = null;

    public Photo(File file) {
        this.file = file;
        this.path = Paths.get(this.file.toURI());
    }

    public File getFile() {
        return this.file;
    }

    public synchronized Instant creationDate() {
        if (loadedCreationDate)
            return creationDate;

        try {
            BasicFileAttributes attributes = Files.readAttributes(this.path, BasicFileAttributes.class);
            loadedCreationDate = true;
            this.creationDate = new Instant(attributes.creationTime().toMillis());
        } catch (IOException e) {
            LOGGER.warn("Photo {} has invalid creation date", this.file.getAbsolutePath());
            loadedCreationDate = true;
        }

        return this.creationDate;
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
