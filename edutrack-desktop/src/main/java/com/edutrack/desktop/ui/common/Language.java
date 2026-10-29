package com.edutrack.desktop.ui.common;

import java.util.Locale;

/** The languages the interface is translated into. English is the default and the fallback. */
public enum Language {

    ENGLISH(Locale.ENGLISH),
    SPANISH(Locale.of("es"));

    private final Locale locale;

    Language(Locale locale) {
        this.locale = locale;
    }

    public Locale locale() {
        return locale;
    }

    /** The language's own name, shown unchanged in every interface language ("Español" stays "Español"). */
    public String nativeName() {
        return switch (this) {
            case ENGLISH -> "English";
            case SPANISH -> "Español";
        };
    }

    /** The saved choice if there is one, else Spanish on a Spanish system, else English. */
    public static Language resolve(String saved, Locale system) {
        if (saved != null) {
            for (Language language : values()) {
                if (language.name().equals(saved)) {
                    return language;
                }
            }
        }
        return system != null && "es".equals(system.getLanguage()) ? SPANISH : ENGLISH;
    }
}
