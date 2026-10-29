package com.edutrack.application.practice;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.practice.RoutineDetails;

/** Input for the practice routine use cases, as typed in the routine dialog. Validated by the domain. */
public final class PracticeCommands {

    private PracticeCommands() {
    }

    /** A new routine goes to the end of its goal's list. {@code timesPerWeek} is ignored for a daily routine. */
    public record CreateRoutine(GoalId goalId, StepId stepId, String title, Cadence cadence, Integer timesPerWeek,
                                Integer targetMinutes) {

        public RoutineDetails details() {
            return new RoutineDetails(stepId, title, cadence, timesPerWeek, targetMinutes);
        }
    }

    public record UpdateRoutine(PracticeRoutineId routineId, StepId stepId, String title, Cadence cadence,
                                Integer timesPerWeek, Integer targetMinutes) {

        public RoutineDetails details() {
            return new RoutineDetails(stepId, title, cadence, timesPerWeek, targetMinutes);
        }
    }
}
