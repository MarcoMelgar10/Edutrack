package com.edutrack.desktop.ui.theme;

import java.util.prefs.Preferences;

/**
 * Remembers the chosen theme per Windows user (registry-backed {@link Preferences}).
 * It is a UI setting, not learning data, so it deliberately stays out of PostgreSQL.
 */
public final class ThemePreferences {

    private static final String KEY = "theme";
    private static final Preferences NODE = Preferences.userNodeForPackage(ThemePreferences.class);

    private ThemePreferences() {
    }

    public static Theme load() {
        try {
            return Theme.valueOf(NODE.get(KEY, Theme.LIGHT.name()));
        } catch (IllegalArgumentException unknownValue) {
            return Theme.LIGHT;
        }
    }

    public static void save(Theme theme) {
        NODE.put(KEY, theme.name());
    }
}
