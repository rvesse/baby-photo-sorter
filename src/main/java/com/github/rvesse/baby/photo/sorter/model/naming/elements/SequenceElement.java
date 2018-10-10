package com.github.rvesse.baby.photo.sorter.model.naming.elements;

import com.github.rvesse.baby.photo.sorter.model.Configuration;
import com.github.rvesse.baby.photo.sorter.model.Photo;
import com.github.rvesse.baby.photo.sorter.utils.StringUtils;

public class SequenceElement implements NamePatternElement {

    @Override
    public String getText(Photo photo, Configuration config) {
        String seqId = Long.toString(photo.getSequenceId());
        if (seqId.length() < config.sequenceIdPadding()) {
            seqId = StringUtils.pad(seqId, config.sequenceIdPadding(), '0');
        }
        return seqId;
    }

    @Override
    public String getPatternText() {
        return "%s";
    }

}
