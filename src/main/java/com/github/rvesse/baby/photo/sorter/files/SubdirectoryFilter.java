package com.github.rvesse.baby.photo.sorter.files;

import java.io.File;
import java.io.FileFilter;

public class SubdirectoryFilter implements FileFilter{

    @Override
    public boolean accept(File f) {
        return f.isDirectory();
    }

}
