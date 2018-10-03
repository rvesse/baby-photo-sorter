package com.github.rvesse.baby.photo.sorter.files;

import java.io.File;
import java.io.FilenameFilter;

import com.github.rvesse.baby.photo.sorter.model.Configuration;

public class ExtensionFilter implements FilenameFilter {
    
    private final Configuration config;
    
    public ExtensionFilter(Configuration config) {
        this.config = config;
    }

    @Override
    public boolean accept(File dir, String name) {
        //System.out.println(String.format("Considering file %s/%s", dir.getAbsolutePath(), name));
        
        return config.hasValidExtension(name);
    }

}
