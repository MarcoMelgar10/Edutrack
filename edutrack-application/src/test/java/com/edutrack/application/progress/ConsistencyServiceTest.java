package com.edutrack.application.progress;

import com.edutrack.domain.progress.StreakCalculator.Streak;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ConsistencyServiceTest {

    /** A Wednesday. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    /** A source over fixed days that, like the real ones, only answers for the asked range. */
    private static StudyDaySource days(LocalDate... days) {
        Set<LocalDate> all = Set.of(days);
        return (from, to) -> all.stream()
                .filter(day -> !day.isBefore(from) && !day.isAfter(to))
                .collect(Collectors.toSet());
    }

    private ConsistencyService service(StudyDaySource... sources) {
        return new ConsistencyService(List.of(sources), clock);
    }

    @Test
    void streakMergesTheDaysOfEverySource() {
        // Sessions on Tuesday and Wednesday, reviews on Monday, a check-in on Sunday: four days in a row.
        ConsistencyService service = service(
                days(TODAY, TODAY.minusDays(1)),
                days(TODAY.minusDays(2)),
                days(TODAY.minusDays(3)));

        assertThat(service.streak()).isEqualTo(new Streak(4, 4, true));
    }

    @Test
    void streakWithoutAnySourceIsEmpty() {
        assertThat(service().streak()).isEqualTo(new Streak(0, 0, false));
    }

    @Test
    void aStreakEndingYesterdayIsStillCurrent() {
        assertThat(service(days(TODAY.minusDays(1), TODAY.minusDays(2))).streak())
                .isEqualTo(new Streak(2, 2, false));
    }

    @Test
    void streakLooksBackOver400Days() {
        List<LocalDate> run = new ArrayList<>();
        for (int back = 0; back < 500; back++) {
            run.add(TODAY.minusDays(back));
        }
        Streak streak = service(days(run.toArray(LocalDate[]::new))).streak();

        assertThat(streak.current()).isEqualTo(401);       // today plus the 400 days back
        assertThat(streak.studiedToday()).isTrue();
    }

    @Test
    void studyDaysAreMergedAndSortedWithinTheRange() {
        ConsistencyService service = service(
                days(LocalDate.of(2026, 10, 20), LocalDate.of(2026, 10, 26)),
                days(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 24)));

        assertThat(service.studyDays(LocalDate.of(2026, 10, 21), TODAY))
                .containsExactly(LocalDate.of(2026, 10, 24), LocalDate.of(2026, 10, 26));
    }

    @Test
    void daysThisWeekCountsDistinctDaysFromTheWeekStart() {
        ConsistencyService service = service(
                days(TODAY, TODAY.minusDays(2), TODAY.minusDays(3)),
                days(TODAY.minusDays(2), TODAY.minusDays(8)));

        // Monday 26 and Wednesday 28 are this week; Sunday 25 and earlier are not.
        assertThat(service.daysThisWeek(DayOfWeek.MONDAY)).isEqualTo(2);
        // With Sunday as the first day, Sunday 25 joins them.
        assertThat(service.daysThisWeek(DayOfWeek.SUNDAY)).isEqualTo(3);
    }

    @Test
    void daysThisWeekIsZeroWithoutActivity() {
        assertThat(service(days()).daysThisWeek(DayOfWeek.MONDAY)).isZero();
    }
}
