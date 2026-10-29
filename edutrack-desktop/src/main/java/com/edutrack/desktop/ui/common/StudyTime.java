package com.edutrack.desktop.ui.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Minutes of study shown as hours with one decimal: "6.5 h" ("6,5 h" in Spanish), "38 h". */
public final class StudyTime {

    private StudyTime() {
    }

    /** {@code minutes} / 60 rounded to one decimal. */
    public static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP);
    }

    /** "6.5 h" */
    public static String hoursText(long minutes) {
        return I18n.get("format.studyHours", Formats.number(hours(minutes)));
    }

    /** "45 min" */
    public static String minutesText(long minutes) {
        return I18n.get("format.studyMinutes", String.valueOf(minutes));
    }
}
