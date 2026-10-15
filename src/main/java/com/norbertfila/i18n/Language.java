package com.norbertfila.i18n;

import java.util.Locale;

public enum Language {
    ENGLISH("en", "English"),
    POLISH("pl", "Polski");

    private final String code;
    private final String displayName;

    Language(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String code() {
        return code;
    }

    public String displayName() {
        return displayName;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    public static Language fromCode(String code) {
        for (Language language : values()) if (language.code.equals(code)) return language;
        return ENGLISH;
    }
}
