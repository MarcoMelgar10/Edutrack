package com.edutrack.desktop.ui.common;

import com.edutrack.domain.area.AreaIcon;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Maps the domain's curated area marks to Feather line icons. */
public final class AreaIcons {

    private AreaIcons() {
    }

    public static Ikon ikon(AreaIcon icon) {
        return switch (icon) {
            case GLOBE -> Feather.GLOBE;
            case CODE -> Feather.CODE;
            case BRIEFCASE -> Feather.BRIEFCASE;
            case BOOK -> Feather.BOOK;
            case CPU -> Feather.CPU;
            case DATABASE -> Feather.DATABASE;
            case USERS -> Feather.USERS;
            case PEN_TOOL -> Feather.PEN_TOOL;
            case BAR_CHART -> Feather.BAR_CHART_2;
            case MESSAGE_SQUARE -> Feather.MESSAGE_SQUARE;
            case AWARD -> Feather.AWARD;
            case COMPASS -> Feather.COMPASS;
            case MUSIC -> Feather.MUSIC;
            case HEADPHONES -> Feather.HEADPHONES;
            case MIC -> Feather.MIC;
            case ACTIVITY -> Feather.ACTIVITY;
            case HEART -> Feather.HEART;
            case CAMERA -> Feather.CAMERA;
            case FILM -> Feather.FILM;
            case COFFEE -> Feather.COFFEE;
            case TOOL -> Feather.TOOL;
            case TARGET -> Feather.TARGET;
            case DOLLAR_SIGN -> Feather.DOLLAR_SIGN;
            case MAP -> Feather.MAP;
            case BOOK_OPEN -> Feather.BOOK_OPEN;
            case FEATHER -> Feather.FEATHER;
            case ZAP -> Feather.ZAP;
            case SMILE -> Feather.SMILE;
            case PEN -> Feather.EDIT_3; // Feather has no plain "pen"; edit-3 is the pen with a baseline
            case TERMINAL -> Feather.TERMINAL;
            case LAYERS -> Feather.LAYERS;
            case TRENDING_UP -> Feather.TRENDING_UP;
        };
    }

    public static FontIcon icon(AreaIcon icon) {
        return new FontIcon(ikon(icon));
    }

    /** The translated name ("Pen tool" / "Lápiz técnico"), for tooltips and screen readers. */
    public static String label(AreaIcon icon) {
        return I18n.get("icon." + icon.name());
    }
}
