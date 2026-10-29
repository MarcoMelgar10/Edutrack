package com.edutrack.domain.progress;

import com.edutrack.domain.progress.StreakCalculator.Streak;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StreakCalculatorTest {

    /** A Wednesday. */
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private static LocalDate daysAgo(int days) {
        return TODAY.minusDays(days);
    }

    @Test
    void noStudyDaysMeansNoStreak() {
        assertThat(StreakCalculator.of(List.of(), TODAY)).isEqualTo(new Streak(0, 0, false));
    }

    @Test
    void streakEndingTodayCountsTodayAndStudiedTodayIsTrue() {
        Streak streak = StreakCalculator.of(List.of(daysAgo(0), daysAgo(1), daysAgo(2)), TODAY);

        assertThat(streak).isEqualTo(new Streak(3, 3, true));
    }

    @Test
    void streakEndingYesterdayIsStillCurrentUntilTheDayIsOver() {
        Streak streak = StreakCalculator.of(List.of(daysAgo(1), daysAgo(2), daysAgo(3)), TODAY);

        assertThat(streak).isEqualTo(new Streak(3, 3, false));
    }

    @Test
    void aMissedDayBreaksTheCurrentStreak() {
        Streak streak = StreakCalculator.of(List.of(daysAgo(2), daysAgo(3), daysAgo(4)), TODAY);

        assertThat(streak.current()).isZero();
        assertThat(streak.longest()).isEqualTo(3);
        assertThat(streak.studiedToday()).isFalse();
    }

    @Test
    void aGapSplitsTheRunAndOnlyTheLatestOneIsCurrent() {
        Streak streak = StreakCalculator.of(
                List.of(daysAgo(0), daysAgo(1), daysAgo(3), daysAgo(4), daysAgo(5), daysAgo(6)), TODAY);

        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.longest()).isEqualTo(4);
    }

    @Test
    void longestRunMayBeOlderThanTheCurrentOne() {
        Streak streak = StreakCalculator.of(
                List.of(daysAgo(0), daysAgo(10), daysAgo(11), daysAgo(12), daysAgo(13), daysAgo(14)), TODAY);

        assertThat(streak.current()).isEqualTo(1);
        assertThat(streak.longest()).isEqualTo(5);
    }

    @Test
    void duplicatesAndOrderDoNotMatter() {
        Streak streak = StreakCalculator.of(List.of(daysAgo(2), daysAgo(0), daysAgo(1), daysAgo(1), daysAgo(0)), TODAY);

        assertThat(streak).isEqualTo(new Streak(3, 3, true));
    }

    @Test
    void futureDaysAreIgnored() {
        Streak streak = StreakCalculator.of(List.of(TODAY.plusDays(1), TODAY.plusDays(2), daysAgo(1)), TODAY);

        assertThat(streak).isEqualTo(new Streak(1, 1, false));
    }

    @Test
    void daysThisWeekCountsFromMonday() {
        // Monday 26, Tuesday 27 and Wednesday 28 are this week; Sunday 25 is last week.
        List<LocalDate> days = List.of(daysAgo(0), daysAgo(1), daysAgo(2), daysAgo(3), daysAgo(10));

        assertThat(StreakCalculator.daysThisWeek(days, TODAY, DayOfWeek.MONDAY)).isEqualTo(3);
    }

    @Test
    void daysThisWeekCountsFromSunday() {
        List<LocalDate> days = List.of(daysAgo(0), daysAgo(1), daysAgo(2), daysAgo(3), daysAgo(4), daysAgo(10));

        assertThat(StreakCalculator.daysThisWeek(days, TODAY, DayOfWeek.SUNDAY)).isEqualTo(4);
    }

    @Test
    void daysThisWeekCountsADayOnceAndIgnoresTheFuture() {
        List<LocalDate> days = List.of(daysAgo(0), daysAgo(0), TODAY.plusDays(1));

        assertThat(StreakCalculator.daysThisWeek(days, TODAY, DayOfWeek.MONDAY)).isEqualTo(1);
    }

    @Test
    void whenTodayIsTheWeekStartOnlyTodayCounts() {
        LocalDate monday = LocalDate.of(2026, 10, 26);

        assertThat(StreakCalculator.daysThisWeek(List.of(monday, monday.minusDays(1)), monday, DayOfWeek.MONDAY))
                .isEqualTo(1);
    }
}
