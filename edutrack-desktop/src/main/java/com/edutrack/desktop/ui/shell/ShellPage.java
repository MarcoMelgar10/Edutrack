package com.edutrack.desktop.ui.shell;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;

/**
 * The fixed pages of the main window, reached from the left rail (area pages come on top of these).
 * Which controller shows each page is decided in one place only: {@link ShellPages}.
 */
public enum ShellPage {

    TODAY(Feather.SUNRISE),
    REVIEW(Feather.LAYERS),
    JOURNAL(Feather.BOOK_OPEN),
    INSIGHTS(Feather.TRENDING_UP),
    SEARCH(Feather.SEARCH),
    SETTINGS(Feather.SETTINGS);

    private final Ikon icon;

    ShellPage(Ikon icon) {
        this.icon = icon;
    }

    public Ikon icon() {
        return icon;
    }

    /** Bundle key of the page's name, e.g. {@code nav.today}. */
    public String titleKey() {
        return "nav." + name().toLowerCase();
    }
}
