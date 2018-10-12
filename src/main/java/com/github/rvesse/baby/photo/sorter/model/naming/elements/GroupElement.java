package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;
import com.github.rvesse.baby.photo.sorter.model.events.Event;

public class GroupElement implements NamePatternElement {

    @Override
    public String getText(Photo photo, Configuration config) {
        Event e = photo.getEvent();
        if (e != null)
            return e.name();
        return photo.getAgeText(config);
    }

    @Override
    public String getPatternText() {
        return "%g";
    }

}
