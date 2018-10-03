package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;

public interface NamePatternElement {

    public String getText(Photo photo, Configuration config);
    
    public String getPatternText();
}
