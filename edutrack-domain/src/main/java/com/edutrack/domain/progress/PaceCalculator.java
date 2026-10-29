package com.edutrack.domain.progress;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Compares the study time a goal is getting with the time its plan needs.
 *
 * <ul>
 *   <li><b>Required</b>: with a deadline and estimated hours left, the hours left spread over the
 *       weeks left (today and the deadline included; an overdue goal needs them all this week).
 *       Otherwise the goal's own weekly target, if it has one (a goal without an end, like
 *       practising an instrument).</li>
 *   <li><b>Actual</b>: logged minutes over the last {@value #WINDOW_DAYS} days, per week. A goal
 *       tracked for less time is averaged over the days it existed, but never over less than one
 *       week, so a single long first session does not look like a fast pace.</li>
 *   <li><b>Status</b>: at least {@value #AHEAD_RATIO}× the required pace is ahead, at least
 *       {@value #ON_TRACK_RATIO}× is on track, below that is behind.</li>
 * </ul>
 */
public final class PaceCalculator {

    public static final int WINDOW_DAYS = 28;
    static final double AHEAD_RATIO = 1.15;
    static final double ON_TRACK_RATIO = 0.9;

    private PaceCalculator() {
    }

    /**
     * @param remainingHours     estimated hours of the steps not done yet; {@code null} when no step is estimated
     * @param deadline           the goal's deadline, or {@code null}
     * @param weeklyTargetMinutes the goal's weekly study target, or {@code null}
     * @param minutesInWindow    minutes logged on the goal in the last {@value #WINDOW_DAYS} days (today included)
     * @param trackingSince      the day the goal was created
     * @param today              the current day
     */
    public static Pace of(BigDecimal remainingHours, LocalDate deadline, Integer weeklyTargetMinutes,
                          int minutesInWindow, LocalDate trackingSince, LocalDate today) {
        Objects.requireNonNull(trackingSince, "trackingSince");
        Objects.requireNonNull(today, "today");
        int actual = actualPerWeek(minutesInWindow, trackingSince, today);

        boolean estimated = remainingHours != null;
        if (estimated && remainingHours.signum() == 0) {
            return new Pace(Pace.PaceStatus.FINISHED, null, actual, null);
        }

        Integer required = null;
        if (deadline != null && estimated) {
            long daysLeft = ChronoUnit.DAYS.between(today, deadline) + 1;
            double weeksLeft = Math.max(1.0, daysLeft / 7.0);
            required = (int) Math.ceil(remainingHours.doubleValue() * 60 / weeksLeft);
        } else if (weeklyTargetMinutes != null && weeklyTargetMinutes > 0) {
            required = weeklyTargetMinutes;
        }

        LocalDate projected = estimated && actual > 0
                ? today.plusDays((long) Math.ceil(remainingHours.doubleValue() * 60 / actual * 7))
                : null;
        if (required == null) {
            return new Pace(Pace.PaceStatus.NO_PLAN, null, actual, projected);
        }
        double ratio = (double) actual / required;
        Pace.PaceStatus status = ratio >= AHEAD_RATIO ? Pace.PaceStatus.AHEAD
                : ratio >= ON_TRACK_RATIO ? Pace.PaceStatus.ON_TRACK
                : Pace.PaceStatus.BEHIND;
        return new Pace(status, required, actual, projected);
    }

    static int actualPerWeek(int minutesInWindow, LocalDate trackingSince, LocalDate today) {
        long tracked = ChronoUnit.DAYS.between(trackingSince, today) + 1;
        long days = Math.clamp(tracked, 7, WINDOW_DAYS);
        return BigDecimal.valueOf(minutesInWindow).multiply(BigDecimal.valueOf(7))
                .divide(BigDecimal.valueOf(days), 0, RoundingMode.HALF_UP).intValueExact();
    }
}
