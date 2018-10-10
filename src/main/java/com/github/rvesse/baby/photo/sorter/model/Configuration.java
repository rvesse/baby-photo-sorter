package com.github.rvesse.baby.photo.sorter.model;

import java.util.List;
import java.util.Locale;

import org.joda.time.Instant;

import com.github.rvesse.baby.photo.sorter.model.naming.NamingPattern;

public class Configuration {

    private final String babyName;
    private final Instant dob;
    private final long weeksThreshold, monthsThreshold, yearsThreshold;
    private final int sequencePadding;
    private final List<String> extensions;
    private final NamingPattern namingPattern;

    public Configuration(Instant dob, String name, long weeksThreshold, long monthsThreshold, long yearsThreshold,
            List<String> extensions, int sequencePadding, NamingPattern namePattern) {
        this.dob = dob;
        this.babyName = name;
        this.weeksThreshold = weeksThreshold;
        this.monthsThreshold = monthsThreshold;
        this.yearsThreshold = yearsThreshold;
        this.extensions = extensions;
        this.sequencePadding = sequencePadding;
        this.namingPattern = namePattern;
    }

    public String babyName() {
        return this.babyName;
    }

    public Instant dateOfBirth() {
        return this.dob;
    }

    public long weeksThreshold() {
        return this.weeksThreshold;
    }

    public long monthsThreshold() {
        return this.monthsThreshold;
    }

    public long yearsThreshold() {
        return this.yearsThreshold;
    }

    public int sequenceIdPadding() {
        return this.sequencePadding;
    }

    public boolean hasValidExtension(String name) {
        for (String ext : this.extensions) {
            if (name.toLowerCase(Locale.ROOT).endsWith(ext.toLowerCase(Locale.ROOT)))
                return true;
        }

        return false;
    }
    
    public NamingPattern namingPattern() {
        return this.namingPattern;
    }
}
