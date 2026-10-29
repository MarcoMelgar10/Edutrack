package com.edutrack.domain.progress;

import com.edutrack.domain.progress.Pace.PaceStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class PaceCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    /** Tracked for much longer than the 28-day window. */
    private static final LocalDate LONG_AGO = TODAY.minusDays(60);

    private static BigDecimal hours(String value) {
        return new BigDecimal(value);
    }

    @Test
    void behindWhenTheDeadlineNeedsMoreThanTheRecentPace() {
        // 20 h left in 14 days (today and the deadline included) = 2 weeks → 600 min/week needed.
        // 900 min over the 28-day window = 225 min/week.
        Pace pace = PaceCalculator.of(hours("20"), TODAY.plusDays(13), null, 900, LONG_AGO, TODAY);

        assertThat(pace.status()).isEqualTo(PaceStatus.BEHIND);
        assertThat(pace.requiredMinutesPerWeek()).isEqualTo(600);
        assertThat(pace.actualMinutesPerWeek()).isEqualTo(225);
        assertThat(pace.projectedFinish()).isEqualTo(TODAY.plusDays(38));
    }

    @Test
    void finishedWhenNoEstimatedHoursRemain() {
        Pace pace = PaceCalculator.of(BigDecimal.ZERO, TODAY.plusDays(13), null, 900, LONG_AGO, TODAY);

        assertThat(pace.status()).isEqualTo(PaceStatus.FINISHED);
        assertThat(pace.requiredMinutesPerWeek()).isNull();
        assertThat(pace.actualMinutesPerWeek()).isEqualTo(225);
        assertThat(pace.projectedFinish()).isNull();
    }

    @Test
    void noPlanWithoutDeadlineAndWeeklyTargetStillProjectsAFinish() {
        Pace pace = PaceCalculator.of(hours("20"), null, null, 900, LONG_AGO, TODAY);

        assertThat(pace.status()).isEqualTo(PaceStatus.NO_PLAN);
        assertThat(pace.requiredMinutesPerWeek()).isNull();
        assertThat(pace.projectedFinish()).isEqualTo(TODAY.plusDays(38));
    }

    @Test
    void aDeadlineWithoutEstimatedHoursIsNoPlan() {
        Pace pace = PaceCalculator.of(null, TODAY.plusDays(30), null, 900, LONG_AGO, TODAY);

        assertThat(pace.status()).isEqualTo(PaceStatus.NO_PLAN);
        assertThat(pace.projectedFinish()).isNull();
    }

    @Test
    void nothingLoggedMeansNoProjection() {
        Pace pace = PaceCalculator.of(hours("20"), TODAY.plusDays(13), null, 0, LONG_AGO, TODAY);

        assertThat(pace.status()).isEqualTo(PaceStatus.BEHIND);
        assertThat(pace.actualMinutesPerWeek()).isZero();
        assertThat(pace.projectedFinish()).isNull();
    }

    @Test
    void aWeeklyTargetWithoutDeadlineIsTheRequiredPace() {
        // 600 min over 28 days = 150 min/week, exactly the target.
        Pace pace = PaceCalculator.of(null, null, 150, 600, LONG_AGO, TODAY);

        assertThat(pace.status()).isEqualTo(PaceStatus.ON_TRACK);
        assertThat(pace.requiredMinutesPerWeek()).isEqualTo(150);
        assertThat(pace.actualMinutesPerWeek()).isEqualTo(150);
    }

    @Test
    void theDeadlinePlanWinsOverTheWeeklyTarget() {
        Pace pace = PaceCalculator.of(hours("20"), TODAY.plusDays(13), 150, 900, LONG_AGO, TODAY);

        assertThat(pace.requiredMinutesPerWeek()).isEqualTo(600);
    }

    @Test
    void aheadFromOnePointFifteenTimesTheRequiredPace() {
        // 460 min / 28 days * 7 = 115 min/week against a target of 100.
        assertThat(PaceCalculator.of(null, null, 100, 460, LONG_AGO, TODAY).status()).isEqualTo(PaceStatus.AHEAD);
        assertThat(PaceCalculator.of(null, null, 100, 456, LONG_AGO, TODAY).status()).isEqualTo(PaceStatus.ON_TRACK);
    }

    @Test
    void onTrackFromNinetyPercentOfTheRequiredPace() {
        // 360 min / 28 days * 7 = 90 min/week against a target of 100.
        assertThat(PaceCalculator.of(null, null, 100, 360, LONG_AGO, TODAY).status()).isEqualTo(PaceStatus.ON_TRACK);
        assertThat(PaceCalculator.of(null, null, 100, 356, LONG_AGO, TODAY).status()).isEqualTo(PaceStatus.BEHIND);
    }

    @Test
    void anOverdueDeadlineNeedsAllTheRemainingHoursThisWeek() {
        Pace pace = PaceCalculator.of(hours("3"), TODAY.minusDays(5), null, 0, LONG_AGO, TODAY);

        assertThat(pace.requiredMinutesPerWeek()).isEqualTo(180);
        assertThat(pace.status()).isEqualTo(PaceStatus.BEHIND);
    }

    @Test
    void aDeadlineTodayNeedsAllTheRemainingHoursThisWeek() {
        Pace pace = PaceCalculator.of(hours("3"), TODAY, null, 0, LONG_AGO, TODAY);

        assertThat(pace.requiredMinutesPerWeek()).isEqualTo(180);
    }

    @Test
    void requiredMinutesRoundUp() {
        // 1 h over 3 weeks = 20 min/week; 1.1 h over 3 weeks = 22 min/week.
        assertThat(PaceCalculator.of(hours("1"), TODAY.plusDays(20), null, 0, LONG_AGO, TODAY)
                .requiredMinutesPerWeek()).isEqualTo(20);
        assertThat(PaceCalculator.of(hours("1.1"), TODAY.plusDays(20), null, 0, LONG_AGO, TODAY)
                .requiredMinutesPerWeek()).isEqualTo(22);
    }

    @Test
    void aNewGoalIsAveragedOverAtLeastOneWeek() {
        // Tracked for 2 days (yesterday and today): 120 min counts over 7 days, not 2.
        Pace pace = PaceCalculator.of(null, null, null, 120, TODAY.minusDays(1), TODAY);

        assertThat(pace.actualMinutesPerWeek()).isEqualTo(120);
    }

    @Test
    void aGoalCreatedTodayIsAveragedOverOneWeek() {
        assertThat(PaceCalculator.of(null, null, null, 300, TODAY, TODAY).actualMinutesPerWeek()).isEqualTo(300);
    }

    @Test
    void aGoalTrackedForTenDaysIsAveragedOverTenDays() {
        // 10 days tracked (the creation day included): 300 min * 7 / 10 = 210.
        assertThat(PaceCalculator.of(null, null, null, 300, TODAY.minusDays(9), TODAY).actualMinutesPerWeek())
                .isEqualTo(210);
    }

    @Test
    void theAverageNeverLooksFurtherBackThanTheWindow() {
        assertThat(PaceCalculator.of(null, null, null, 280, TODAY.minusDays(400), TODAY).actualMinutesPerWeek())
                .isEqualTo(70);
    }
}
