package com.edutrack.application.practice;

import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.PracticeRoutineId;

/** One practice routine as the lists show it; {@code stepTitle} is {@code null} without a step. */
public record RoutineView(PracticeRoutineId id, GoalId goalId, StepId stepId, String stepTitle, String title,
                          Cadence cadence, int timesPerWeek, int targetMinutes, boolean active, int position) {

    /** {@code goal} is the routine's goal, used to resolve the step title; it may be {@code null}. */
    static RoutineView of(PracticeRoutine routine, Goal goal) {
        String stepTitle = goal == null || routine.stepId() == null ? null : goal.steps().stream()
                .filter(step -> step.id().equals(routine.stepId()))
                .map(RoadmapStep::title)
                .findFirst()
                .orElse(null);
        return new RoutineView(routine.id(), routine.goalId(), routine.stepId(), stepTitle, routine.title(),
                routine.cadence(), routine.timesPerWeek(), routine.targetMinutes(), routine.active(),
                routine.position());
    }
}
