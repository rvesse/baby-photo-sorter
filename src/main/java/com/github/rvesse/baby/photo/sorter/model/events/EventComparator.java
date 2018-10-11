package com.github.rvesse.baby.photo.sorter.model.events;

import java.util.Comparator;

public class EventComparator implements Comparator<Event> {

    @Override
    public int compare(Event e1, Event e2) {
        if (e1 == null) {
            if (e2 == null) return 0;
            return -1;
        } else if (e2 == null) {
            return 1;
        }
        
        if (e1.start().isBefore(e2.start())) {
            return -1;
        } else if (e1.start().isEqual(e2.start())) {
            if (e1.end().isBefore(e2.end())) {
                return -1;
            } else if (e1.end().isEqual(e2.end())) {
                return 0;
            } else {
                return 1;
            }
        } else {
            return 1;
        }
           
    }

}
