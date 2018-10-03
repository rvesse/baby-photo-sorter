package com.github.rvesse.baby.photo.sorter.model;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;

import org.joda.time.Days;
import org.joda.time.Instant;
import org.joda.time.Period;
import org.joda.time.Weeks;

public class Photo {

    private final File file;
    private final Path path;
    private boolean loadedCreationDate = false;
    private Instant creationDate = null;

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
            Period p = new Period(config.dateOfBirth(), i);
            try {
                Days days = p.toStandardDays();
                return Math.max(days.getDays(), p.getDays());
            } catch (UnsupportedOperationException e) {
                return -1;
            }
        }
        return -1;
    }

    public long ageInWeeks(Configuration config) {
        Instant i = creationDate();
        if (i != null) {
            Period p = new Period(config.dateOfBirth(), i);
            try {
                Weeks weeks = p.toStandardWeeks();
                return Math.max(weeks.getWeeks(), p.getWeeks());
            } catch (UnsupportedOperationException e) {
                long months = ageInMonths(config);
                if (months != -1) {
                    return months * 4;
                }
            }
        }
        return -1;
    }

    public long ageInMonths(Configuration config) {
        Instant i = creationDate();
        return i != null ? new Period(config.dateOfBirth(), i).getMonths() : -1;
    }

    public long ageInYears(Configuration config) {
        Instant i = creationDate();
        return i != null ? new Period(config.dateOfBirth(), i).getYears() : -1;
    }

    public String getAgeText(Configuration config) {
        Instant i = creationDate();
        if (i.isBefore(config.dateOfBirth()))
            return "Pregnancy";

        long days = ageInDays(config);
        if (days != -1 && days / 7 < config.weeksThreshold()) {
            return String.format("%d Days", days);
        }
        long weeks = ageInWeeks(config);
        long months = ageInMonths(config);
        if (weeks != -1 && weeks >= config.weeksThreshold() && months < config.monthsThreshold()) {
            return String.format("%d Weeks", weeks);
        } else if (months != -1 && months >= config.monthsThreshold() && (months / 12) < config.yearsThreshold()) {
            return String.format("%d Months", months);
        }
        long years = ageInYears(config);
        if (years != -1) {
            return String.format("%d Years", years);
        } else {
            return "Unknown";
        }
    }
}
