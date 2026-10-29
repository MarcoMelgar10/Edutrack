package com.edutrack.domain.progress;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Consistency over days. A study day is any day with a logged session, a card review or a
 * check-in; the caller collects them.
 *
 * <p>The current streak counts the consecutive study days that end today. Until something is
 * done today, a streak that ended yesterday is still current: the day is not over yet.
 */
public final class StreakCalculator {

    private StreakCalculator() {
    }

    public record Streak(int current, int longest, boolean studiedToday) {
    }

    public static Streak of(Collection<LocalDate> studyDays, LocalDate today) {
        Objects.requireNonNull(today, "today");
        TreeSet<LocalDate> days = new TreeSet<>(studyDays);
        days.removeIf(day -> day.isAfter(today));
        boolean studiedToday = days.contains(today);

        int current = 0;
        for (LocalDate day = studiedToday ? today : today.minusDays(1); days.contains(day); day = day.minusDays(1)) {
            current++;
        }

        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate day : days) {
            run = previous != null && previous.plusDays(1).equals(day) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = day;
        }
        return new Streak(current, longest, studiedToday);
    }

    /** Study days in the week that contains {@code today}, the week starting on {@code weekStart}. */
    public static int daysThisWeek(Collection<LocalDate> studyDays, LocalDate today, DayOfWeek weekStart) {
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(weekStart));
        return (int) studyDays.stream().distinct()
                .filter(day -> !day.isBefore(start) && !day.isAfter(today))
                .count();
    }
}
