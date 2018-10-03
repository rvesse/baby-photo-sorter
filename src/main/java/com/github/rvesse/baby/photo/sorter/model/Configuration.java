package com.github.rvesse.baby.photo.sorter.model;

import java.util.List;
import java.util.Locale;

import org.joda.time.Instant;

public class Configuration {

    private final Instant dob;
    private final long weeksThreshold, monthsThreshold, yearsThreshold;
    private final List<String> extensions;
    
    public Configuration(Instant dob, long weeksThreshold, long monthsThreshold, long yearsThreshold, List<String> extensions) {
        this.dob = dob;
        this.weeksThreshold = weeksThreshold;
        this.monthsThreshold = monthsThreshold;
        this.yearsThreshold = yearsThreshold;
        this.extensions = extensions;
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
    
    public boolean hasValidExtension(String name) {
        for (String ext : this.extensions) {
            if (name.toLowerCase(Locale.ROOT).endsWith(ext.toLowerCase(Locale.ROOT)))
                return true;
        }
        
        return false;
    }
}
