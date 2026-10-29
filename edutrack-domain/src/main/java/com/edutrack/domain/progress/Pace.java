package com.edutrack.domain.progress;

import java.time.LocalDate;

/**
 * How fast a goal is being studied compared with what its plan needs.
 *
 * @param status                 the verdict
 * @param requiredMinutesPerWeek study time the plan needs each week; {@code null} without a plan
 * @param actualMinutesPerWeek   average logged study time over the recent weeks
 * @param projectedFinish        when the remaining estimated hours would be done at the actual pace;
 *                               {@code null} when nothing is estimated, nothing remains or nothing was logged
 */
public record Pace(PaceStatus status, Integer requiredMinutesPerWeek, int actualMinutesPerWeek,
                   LocalDate projectedFinish) {

    public enum PaceStatus {
        /** No deadline with estimated hours and no weekly target: nothing to compare with. */
        NO_PLAN,
        /** Every estimated hour is done. */
        FINISHED,
        AHEAD,
        ON_TRACK,
        BEHIND
    }
}
