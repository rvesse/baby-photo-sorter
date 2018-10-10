package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;

public class BabyNameElement implements NamePatternElement {

    @Override
    public String getText(Photo photo, Configuration config) {
        return config.babyName();
    }

    @Override
    public String getPatternText() {
        return "%n";
    }

}
