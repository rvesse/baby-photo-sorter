package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;

public class AgeElement implements NamePatternElement {

    @Override
    public String getText(Photo photo, Configuration config) {
        return photo.getAgeText(config);
    }

    @Override
    public String getPatternText() {
        return "%a";
    }

}
