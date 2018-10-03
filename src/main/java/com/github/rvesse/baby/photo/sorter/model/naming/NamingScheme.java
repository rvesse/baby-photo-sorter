package com.github.rvesse.baby.photo.sorter.model.naming;

import com.github.rvesse.baby.photo.sorter.model.naming.elements.*;

/**
 * Default built-in naming schemes
 * @author rvesse
 *
 */
public enum NamingScheme {
    NameAgeSequence(new NamingPattern(new BabyNameElement(), new AgeElement(), new SequenceElement())),
    AgeNameSequence(new NamingPattern(new AgeElement(), new BabyNameElement(), new SequenceElement())),
    NameAgeDate(new NamingPattern(new BabyNameElement(), new AgeElement(), new DateElement()));
    
    private final NamingPattern pattern;
    
    private NamingScheme(NamingPattern pattern) {
        this.pattern = pattern;
    }
    
    public NamingPattern getPattern() {
        return this.pattern;
    }
}
