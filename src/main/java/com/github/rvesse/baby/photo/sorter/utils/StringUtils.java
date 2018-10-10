package com.github.rvesse.baby.photo.sorter.utils;

public class StringUtils {

    public static String pad(String value, int minLength, char padChar) {
        if (value.length() >= minLength)
            return value;

        StringBuilder builder = new StringBuilder();
        int requiredPadding = minLength - value.length();
        while (requiredPadding > 0) {
            builder.append(padChar);
            requiredPadding--;
        }
        builder.append(value);
        return builder.toString();
    }
}
