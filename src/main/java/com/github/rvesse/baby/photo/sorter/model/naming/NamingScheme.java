package com.github.rvesse.baby.photo.sorter.model.naming;

/**
 * Default built-in naming schemes
 * @author rvesse
 *
 */
public enum NamingScheme {
    //@formatter:off
    NameAgeSequence(new NamingPatternBuilder()
                        .appendBabyName()
                        .appendSpace()
                        .appendBabyAge()
                        .appendSpace()
                        .appendSequenceId()
                        .build()),
    AgeNameSequence(new NamingPatternBuilder()
                        .appendBabyAge()
                        .appendSpace()
                        .appendBabyName()
                        .appendSpace()
                        .appendSequenceId()
                        .build()),
    NameAgeDate(new NamingPatternBuilder()
                    .appendBabyName()
                    .appendSpace()
                    .appendBabyAge()
                    .appendSpace()
                    .appendDate()
                    .build());
    //@formatter:on
    
    private final NamingPattern pattern;
    
    private NamingScheme(NamingPattern pattern) {
        this.pattern = pattern;
    }
    
    public NamingPattern getPattern() {
        return this.pattern;
    }
}
