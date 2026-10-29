package com.edutrack.application.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.ProgressCalculator;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.RecordSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/**
 * Everything the goal detail panel shows: header, progress ("26% · 10 / 38 h") and the roadmap.
 * {@code doneHours}/{@code totalHours} are only the hours the learner estimated; steps without an
 * estimate are counted in {@code unestimatedSteps} instead (the percentage still weighs them 1 h).
 * {@code weeklyTargetMinutes} is the goal's optional weekly study target.
 */
public record GoalDetails(GoalId id, AreaId areaId, String title, String description, LocalDate deadline,
                          Progress progress, BigDecimal doneHours, BigDecimal totalHours, int unestimatedSteps,
                          boolean completed, List<StepView> steps, Integer weeklyTargetMinutes, Instant createdAt) {

    public GoalDetails(GoalId id, AreaId areaId, String title, String description, LocalDate deadline,
                       Progress progress, BigDecimal doneHours, BigDecimal totalHours, int unestimatedSteps,
                       boolean completed, List<StepView> steps) {
        this(id, areaId, title, description, deadline, progress, doneHours, totalHours, unestimatedSteps,
                completed, steps, null, null);
    }

    static GoalDetails of(Goal goal, Map<StepId, RecordSummary> records) {
        ProgressCalculator.Effort effort = goal.effort();
        List<StepView> steps = IntStream.range(0, goal.steps().size())
                .mapToObj(index -> {
                    RoadmapStep step = goal.steps().get(index);
                    return StepView.of(step, index + 1, records.getOrDefault(step.id(), RecordSummary.EMPTY));
                })
                .toList();
        return new GoalDetails(goal.id(), goal.areaId(), goal.title(), goal.description(), goal.deadline(),
                effort.progress(), effort.estimatedDoneHours(), effort.estimatedHours(), effort.unestimatedSteps(),
                goal.isCompleted(), steps, goal.weeklyTargetMinutes(), goal.createdAt());
    }

    /** The step with this id, e.g. to show its header on the step page. */
    public StepView step(StepId stepId) {
        return steps.stream()
                .filter(step -> step.id().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step " + stepId + " is not part of goal " + id));
    }
}
