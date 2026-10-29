package com.edutrack.application.progress;

import com.edutrack.domain.progress.StreakCalculator;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Consistency over days (streak, days studied this week), merging every {@link StudyDaySource}:
 * sessions, card reviews, check-ins. Framework-free; wired as a bean by the desktop module.
 */
public class ConsistencyService {

    /** How far back the streak looks; longer runs are cut off, which no screen would notice. */
    static final int LOOKBACK_DAYS = 400;

    private final List<StudyDaySource> sources;
    private final Clock clock;

    public ConsistencyService(List<StudyDaySource> sources, Clock clock) {
        this.sources = List.copyOf(sources);
        this.clock = clock;
    }

    public StreakCalculator.Streak streak() {
        LocalDate today = LocalDate.now(clock);
        return StreakCalculator.of(studyDays(today.minusDays(LOOKBACK_DAYS), today), today);
    }

    /** The days from {@code from} to {@code to}, both included, with any activity, oldest first. */
    public Set<LocalDate> studyDays(LocalDate from, LocalDate to) {
        Set<LocalDate> days = new TreeSet<>();
        for (StudyDaySource source : sources) {
            days.addAll(source.studyDays(from, to));
        }
        return days;
    }

    /** Study days in the week that contains today, the week starting on {@code weekStart}. */
    public int daysThisWeek(DayOfWeek weekStart) {
        LocalDate today = LocalDate.now(clock);
        LocalDate first = today.with(TemporalAdjusters.previousOrSame(weekStart));
        return StreakCalculator.daysThisWeek(studyDays(first, today), today, weekStart);
    }
}
