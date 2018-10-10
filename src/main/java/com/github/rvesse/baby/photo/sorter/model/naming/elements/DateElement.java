package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import org.joda.time.Instant;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;

public class DateElement implements NamePatternElement {

    @Override
    public String getText(Photo photo, Configuration config) {
        Instant creationDate = photo.creationDate();
        if (creationDate != null) {
            // TODO Use configured output date format
            return creationDate.toString();
        }
        return "";
    }

    @Override
    public String getPatternText() {
        return "%d";
    }

}
