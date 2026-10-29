package com.edutrack.domain.practice;

import com.edutrack.domain.goal.StepId;

/**
 * The editable content of a practice routine, as entered by the user. Not validated here;
 * {@link PracticeRoutine} validates and normalizes it when it is applied. {@code timesPerWeek} is
 * ignored for a daily routine.
 */
public record RoutineDetails(StepId stepId, String title, Cadence cadence, Integer timesPerWeek,
                             Integer targetMinutes) {
}
