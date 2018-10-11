package com.github.rvesse.baby.photo.sorter.model;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.joda.time.Instant;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.rvesse.baby.photo.sorter.model.events.Event;
import com.github.rvesse.baby.photo.sorter.model.events.EventComparator;

public class Events {

    private static final Logger LOGGER = LoggerFactory.getLogger(Events.class);

    private final List<Event> events = new ArrayList<>();

    public Events() {
        // No events
    }

    public Events(Collection<Event> events) {
        this.events.addAll(events);
        this.events.sort(new EventComparator());
    }

    /**
     * Gets whether something is in an event
     * 
     * @param photo
     *            Photo
     * @return Event, or {@code null} if not in any event
     */
    public Event inEvent(Photo photo) {
        for (Event e : this.events) {
            if (e.inEvent(photo))
                return e;
        }

        return null;
    }

    public static Events parse(File f, DateTimeFormatter formatter) {
        List<Event> events = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",", 3);
                
                // Add start and end time if missing
                if (!parts[0].contains(" ")) {
                    parts[0] += " 00:00:00Z";
                }
                if (!parts[1].contains(" ")) {
                    parts[1] += " 23:59:59Z";
                }
                
                // Pass the dates and create the event
                Instant start = Instant.parse(parts[0], formatter);
                Instant end = Instant.parse(parts[1], formatter);
                events.add(new Event(start, end, parts[2]));
            }
        } catch (Exception e) {
            LOGGER.error("Failed to parse events file {} - {}", f.getAbsolutePath(), e.getMessage());
            System.exit(1);
        }

        return new Events(events);
    }
}
