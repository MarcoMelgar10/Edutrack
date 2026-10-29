package com.edutrack.desktop.ui.common;

import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.ResourceType;

import java.math.BigDecimal;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * How numbers, dates and statuses read in the UI. Formal and consistent everywhere, in the language
 * chosen in Settings: patterns, words and plural forms come from the bundle through {@link I18n}.
 */
public final class Formats {

    private Formats() {
    }

    /** 01 Mar 2027 / 01 mar 2027 */
    public static String date(LocalDate date) {
        return date == null ? "" : formatter("format.date").format(date);
    }

    public static String date(Instant instant, ZoneId zone) {
        return instant == null ? "" : formatter("format.date").format(instant.atZone(zone));
    }

    /** Mar 2027 — compact form for goal cards. */
    public static String month(LocalDate date) {
        return date == null ? I18n.get("deadline.none") : formatter("format.month").format(date);
    }

    /** Tuesday, 27 October 2026 — the heading of the Today page. */
    public static String longDate(LocalDate date) {
        String text = formatter("format.longDate").format(date);
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /** 14:32 */
    public static String time(Instant instant, ZoneId zone) {
        return formatter("format.time").format(instant.atZone(zone));
    }

    /** "Deadline 01 Mar 2027 · 125 days left", "· due today", "· 3 days overdue", or "No deadline". */
    public static String deadline(LocalDate deadline, LocalDate today) {
        if (deadline == null) {
            return I18n.get("deadline.none");
        }
        long days = ChronoUnit.DAYS.between(today, deadline);
        String remaining;
        if (days == 0) {
            remaining = I18n.get("deadline.dueToday");
        } else if (days > 0) {
            remaining = I18n.plural("deadline.daysLeft", days);
        } else {
            remaining = I18n.plural("deadline.daysOverdue", -days);
        }
        return I18n.get("deadline.line", date(deadline), remaining);
    }

    /** 10 → "10h", 2.5 → "2.5h" ("2,5h" in Spanish), none → "" */
    public static String hours(BigDecimal hours) {
        return hours == null ? "" : I18n.get("format.hours", number(hours));
    }

    /**
     * The hours behind a goal, showing only hours the learner entered:
     * "10 / 38 h", "33 / 194.5 h · 1 step without estimate", or "1 of 4 steps" when nothing is estimated.
     */
    public static String effort(BigDecimal doneHours, BigDecimal totalHours, int unestimatedSteps,
                                int doneSteps, int steps) {
        if (steps > 0 && unestimatedSteps == steps) {
            return I18n.get("effort.stepsOnly", doneSteps, steps);
        }
        String hours = I18n.get("effort.hours", number(doneHours), number(totalHours));
        return unestimatedSteps == 0 ? hours
                : hours + " · " + I18n.plural("effort.withoutEstimate", unestimatedSteps);
    }

    /** "https://www.bbc.co.uk/learningenglish/" → "bbc.co.uk/learningenglish" — calmer to read. */
    public static String link(String url) {
        if (url == null) {
            return null;
        }
        String shown = url.replaceFirst("^https?://", "").replaceFirst("^www\\.", "");
        return shown.endsWith("/") ? shown.substring(0, shown.length() - 1) : shown;
    }

    /** "Notes · 3 resources · Exercises 2/5 · 1 result" under a timeline step, or "" when nothing is recorded. */
    public static String recordSummary(RecordSummary summary) {
        List<String> parts = new ArrayList<>();
        if (summary.hasNotes()) {
            parts.add(I18n.get("record.notes"));
        }
        if (summary.resourceCount() > 0) {
            parts.add(I18n.plural("count.resources", summary.resourceCount()));
        }
        if (summary.exercisesTotal() > 0) {
            parts.add(I18n.get("record.exercises", summary.exercisesDone(), summary.exercisesTotal()));
        }
        if (summary.resultCount() > 0) {
            parts.add(I18n.plural("count.results", summary.resultCount()));
        }
        return String.join(" · ", parts);
    }

    /** "6.5 / 9 · 72%" with a maximum, "85" without one. */
    public static String score(BigDecimal score, BigDecimal maxScore, Progress percentage) {
        if (maxScore == null) {
            return number(score);
        }
        String base = number(score) + " / " + number(maxScore);
        return percentage == null ? base : base + " · " + percent(percentage);
    }

    public static String resourceType(ResourceType type) {
        return I18n.get("resource.type." + type.name());
    }

    public static String percent(Progress progress) {
        return progress.rounded() + "%";
    }

    public static String status(StepStatus status) {
        return I18n.get("status." + status.name());
    }

    /** 2.50 → "2.5", or "2,5" in Spanish; whole numbers carry no decimals. */
    public static String number(BigDecimal value) {
        String plain = value.stripTrailingZeros().toPlainString();
        char separator = DecimalFormatSymbols.getInstance(I18n.locale()).getDecimalSeparator();
        return separator == '.' ? plain : plain.replace('.', separator);
    }

    private static DateTimeFormatter formatter(String patternKey) {
        return DateTimeFormatter.ofPattern(I18n.get(patternKey), I18n.locale());
    }
}
