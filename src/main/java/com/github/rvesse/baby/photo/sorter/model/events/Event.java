package com.github.rvesse.baby.photo.sorter.model.events;

import org.joda.time.Instant;

import com.github.rvesse.baby.photo.sorter.model.Photo;

public class Event {

    private final Instant start, end;
    private final String name;
    private long photosInEvent = 0;
    
    public Event(Instant start, Instant end, String eventName) {
        if (start == null || end == null)
            throw new IllegalArgumentException("start and end cannot be null");
        if (!start.isBefore(end))
            throw new IllegalArgumentException("start must be before end");
        this.start = start;
        this.end = end;
        this.name = eventName;
    }

    public boolean inEvent(Photo photo) {
        Instant creationDate = photo.creationDate();
        if (creationDate == null)
            return false;

        return creationDate.isAfter(this.start) && creationDate.isBefore(this.end);
    }

    public Instant start() {
        return this.start;
    }

    public Instant end() {
        return this.end;
    }

    public String name() {
        return this.name;
    }
    
    public void increment() {
        this.photosInEvent++;
    }
    
    public long size() {
        return this.photosInEvent;
    }
}
