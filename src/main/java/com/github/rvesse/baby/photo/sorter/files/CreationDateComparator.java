package com.github.rvesse.baby.photo.sorter.files;

import java.util.Comparator;

import org.joda.time.Instant;

import com.github.rvesse.baby.photo.sorter.model.Photo;

public class CreationDateComparator implements Comparator<Photo> {

    @Override
    public int compare(Photo p, Photo q) {
        if (p == q) return 0;
        if (p == null) {
            if (q == null) return 0;
            return -1;
        } else if (q == null) {
            return 1;
        }
        
        Instant pCreation = p.creationDate();
        Instant qCreation = q.creationDate();
        
        if (pCreation == null) {
            if (qCreation == null) {
                return p.getFile().compareTo(q.getFile());
            } else {
                return -1;
            }
        } else if (qCreation == null) {
            return 1;
        }
        
        return pCreation.compareTo(qCreation);
    }

}
