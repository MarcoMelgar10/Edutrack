package com.edutrack.desktop.ui.session;

import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * How study time reads: a running clock ("42:10"), a duration ("45 min", "4.5 h"), a weekly rate
 * ("5.5 h/week") and the short dates of the agenda. Words and patterns come from the bundle.
 */
public final class SessionFormats {

    private SessionFormats() {
    }

    /** 42:10, or 1:02:10 from one hour on. */
    public static String clock(long totalSeconds) {
        long seconds = Math.max(0, totalSeconds);
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long rest = seconds % 60;
        return hours > 0 ? "%d:%02d:%02d".formatted(hours, minutes, rest) : "%d:%02d".formatted(minutes, rest);
    }

    /** 45 → "45 min", 270 → "4.5 h", 2460 → "41 h". */
    public static String duration(int minutes) {
        return minutes < 60 ? I18n.get("session.duration.minutes", minutes)
                : I18n.get("session.duration.hours", hoursOf(minutes));
    }

    /** 330 → "5.5 h/week", 30 → "30 min/week". */
    public static String perWeek(int minutes) {
        return minutes < 60 ? I18n.get("session.perWeek.minutes", minutes)
                : I18n.get("session.perWeek.hours", hoursOf(minutes));
    }

    /** "12 Mar" */
    public static String dayMonth(LocalDate date) {
        return formatter("format.dayMonth").format(date);
    }

    /** "Wed 28 Oct" */
    public static String dayDate(LocalDate date) {
        return formatter("format.dayDate").format(date);
    }

    /** Minutes as hours with at most one decimal, in the language's number style. */
    private static String hoursOf(int minutes) {
        return Formats.number(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_UP));
    }

    private static DateTimeFormatter formatter(String patternKey) {
        return DateTimeFormatter.ofPattern(I18n.get(patternKey), I18n.locale());
    }
}
