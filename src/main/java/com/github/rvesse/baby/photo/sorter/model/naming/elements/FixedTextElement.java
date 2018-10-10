package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;

public class FixedTextElement implements NamePatternElement {
    
    private final String text;
    
    public FixedTextElement(String text) {
        this.text = text;
    }

    @Override
    public String getText(Photo photo, Configuration config) {
        return this.text;
    }

    @Override
    public String getPatternText() {
        return this.text;
    }

}
