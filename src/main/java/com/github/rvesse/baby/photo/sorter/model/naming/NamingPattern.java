package com.github.rvesse.baby.photo.sorter.model.naming;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.NamePatternElement;

public class NamingPattern {

    private final List<NamePatternElement> elements = new ArrayList<>();
    
    public NamingPattern(Collection<NamePatternElement> elements) {
        this.elements.addAll(elements);
    }
    
    public NamingPattern(NamePatternElement...elements) {
        for (NamePatternElement e : elements) {
            this.elements.add(e);
        }
    }
    
    public String getName(Photo photo, Configuration config) {
        return null;
    }
}
