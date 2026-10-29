package com.edutrack.domain.area;

/**
 * The curated set of marks an area can carry. The domain only knows the names;
 * the desktop UI maps each one to a Feather line icon. Stored by name in the database,
 * with no constraint, so the set can keep growing.
 */
public enum AreaIcon {
    GLOBE,
    CODE,
    BRIEFCASE,
    BOOK,
    CPU,
    DATABASE,
    USERS,
    PEN_TOOL,
    BAR_CHART,
    MESSAGE_SQUARE,
    AWARD,
    COMPASS,
    MUSIC,
    HEADPHONES,
    MIC,
    ACTIVITY,
    HEART,
    CAMERA,
    FILM,
    COFFEE,
    TOOL,
    TARGET,
    DOLLAR_SIGN,
    MAP,
    BOOK_OPEN,
    FEATHER,
    ZAP,
    SMILE,
    PEN,
    TERMINAL,
    LAYERS,
    TRENDING_UP
}
