package com.github.rvesse.baby.photo.sorter.model;

import java.util.List;
import java.util.Locale;

import org.joda.time.Duration;
import org.joda.time.Instant;

import com.github.rvesse.baby.photo.sorter.model.naming.NamingPattern;

public class Configuration {

    private final String babyName;
    private final Instant dob, dueDate;
    private final long weeksThreshold, monthsThreshold, yearsThreshold, weeksOfPregnancy;
    private final Events events;
    private final int sequencePadding;
    private final List<String> extensions;
    private final NamingPattern namingPattern;

    public Configuration(Instant dob, Instant dueDate, String name, long weeksThreshold, long monthsThreshold, long yearsThreshold,
            Events events, List<String> extensions, int sequencePadding, NamingPattern namePattern) {
        this.dob = dob;
        this.dueDate = dueDate;
        this.babyName = name;
        this.weeksThreshold = weeksThreshold;
        this.monthsThreshold = monthsThreshold;
        this.yearsThreshold = yearsThreshold;
        this.events = events;
        this.extensions = extensions;
        this.sequencePadding = sequencePadding;
        this.namingPattern = namePattern;
        
        if (this.dob.isEqual(this.dueDate) || this.dob.isBefore(this.dueDate)) {
            this.weeksOfPregnancy = 39;
        } else {
            Duration lateness = new Duration(this.dueDate, this.dateOfBirth());
            this.weeksOfPregnancy = 39 + (lateness.getStandardDays() / 7);
        }
    }

    public String babyName() {
        return this.babyName;
    }

    public Instant dateOfBirth() {
        return this.dob;
    }
    
    public Instant dueDate() {
        return this.dueDate;
    }
    
    public long weeksOfPregnancy() {
        return this.weeksOfPregnancy;
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
    
    public Events events() {
        return this.events;
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
