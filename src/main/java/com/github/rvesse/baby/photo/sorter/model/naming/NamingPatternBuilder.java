package com.github.rvesse.baby.photo.sorter.model.naming;

import java.util.ArrayList;
import java.util.List;

import com.github.rvesse.baby.photo.sorter.model.naming.elements.AgeElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.BabyNameElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.DateElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.FixedTextElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.GroupElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.NamePatternElement;
import com.github.rvesse.baby.photo.sorter.model.naming.elements.SequenceElement;

public class NamingPatternBuilder {

    private List<NamePatternElement> elements = new ArrayList<>();
    
    public NamingPatternBuilder appendText(String text) {
        return appendElement(new FixedTextElement(text));
    }
    
    public NamingPatternBuilder appendText(char text) {
        return appendElement(new FixedTextElement(new String(new char[] { text })));
    }
    
    public NamingPatternBuilder appendSpace() {
        return appendText(' ');
    }
    
    public NamingPatternBuilder appendBabyName() {
        return appendElement(new BabyNameElement());
    }
    
    public NamingPatternBuilder appendBabyAge() {
        return appendElement(new AgeElement());
    }
    
    public NamingPatternBuilder appendDate() {
        return appendElement(new DateElement());
    }
    
    public NamingPatternBuilder appendSequenceId() {
        return appendElement(new SequenceElement());
    }
    
    public NamingPatternBuilder appendGroupName() {
        return appendElement(new GroupElement());
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
                    case 'g':
                        builder.appendGroupName();
                        break;
                    default:
                        // Not a format specifier
                        // Append literal % and then the subsequent character
                        temp.append('%');
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
