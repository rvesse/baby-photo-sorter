package com.github.rvesse.baby.photo.sorter.model.naming;

import java.util.ArrayList;
import java.util.List;

import com.github.rvesse.baby.photo.sorter.model.naming.elements.AgeElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.BabyNameElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.DateElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.FixedTextElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.NamePatternElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.SequenceElement;

public class NamingPatternBuilder {

    private List<NamePatternElement> elements = new ArrayList<>();
    
    public NamingPatternBuilder appendText(String text) {
        this.elements.add(new FixedTextElement(text));
        return this;
    }
    
    public NamingPatternBuilder appendText(char text) {
        this.elements.add(new FixedTextElement(new String(new char[] { text })));
        return this;
    }
    
    public NamingPatternBuilder appendSpace() {
        return appendText(' ');
    }
    
    public NamingPatternBuilder appendBabyName() {
        this.elements.add(new BabyNameElement());
        return this;
    }
    
    public NamingPatternBuilder appendBabyAge() {
        this.elements.add(new AgeElement());
        return this;
    }
    
    public NamingPatternBuilder appendDate() {
        this.elements.add(new DateElement());
        return this;
    }
    
    public NamingPatternBuilder appendSequenceId() {
        this.elements.add(new SequenceElement());
        return this;
    }
    
    public NamingPatternBuilder appendElement(NamePatternElement element) {
        this.elements.add(element);
        return this;
    }
    
    public NamingPattern build() {
        return new NamingPattern(new ArrayList<>(elements));
    }
    
    public static NamingPattern parse(String pattern) {
        NamingPatternBuilder builder = new NamingPatternBuilder();
        char[] cs = pattern.toCharArray();
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < cs.length; i++) {
            if (cs[i] == '%') {
                if (i < cs.length - 1) {
                    if (temp.length() > 0) {
                        builder.appendText(temp.toString());
                        temp = new StringBuilder();
                    }
                    
                    char patternChar = cs[++i];
                    switch (patternChar) {
                    case 's':
                        builder.appendSequenceId();
                        break;
                    case 'a':
                        builder.appendBabyAge();
                        break;
                    case 'n':
                        builder.appendBabyName();
                        break;
                    case 'd':
                        builder.appendDate();
                        break;
                    default:
                        temp.append(cs[i]);
                        break;
                    }
                } else {
                    temp.append(cs[i]);
                }
            } else {
                temp.append(cs[i]);
            }
        }
        
        if (temp.length() > 0) {
            builder.appendText(temp.toString());
            temp = new StringBuilder();
        }
        
        return builder.build();
    }
}
