package com.edutrack.domain.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Aggregate root: a learning goal inside an area, with its ordered roadmap.
 *
 * <p>All changes to roadmap steps go through the goal, so the step order (the list order,
 * stored as the step position) and the completion dates always stay consistent.
 */
public final class Goal {

    public static final int TITLE_MAX_LENGTH = 120;
    /** A week has 10 080 minutes. */
    public static final int WEEKLY_TARGET_MAX_MINUTES = 7 * 24 * 60;

    private final GoalId id;
    private final AreaId areaId;
    private final Instant createdAt;
    private final List<RoadmapStep> steps;
    private String title;
    private String description;
    private LocalDate deadline;
    private Integer weeklyTargetMinutes;
    private int position;

    private Goal(GoalId id, AreaId areaId, String title, String description, LocalDate deadline,
                 Integer weeklyTargetMinutes, int position, Instant createdAt, List<RoadmapStep> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.areaId = Objects.requireNonNull(areaId, "areaId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.steps = new ArrayList<>(steps);
        update(title, description, deadline, weeklyTargetMinutes);
        moveTo(position);
    }

    public static Goal create(AreaId areaId, String title, String description, LocalDate deadline,
                              int position, Instant now) {
        return create(areaId, title, description, deadline, null, position, now);
    }

    public static Goal create(AreaId areaId, String title, String description, LocalDate deadline,
                              Integer weeklyTargetMinutes, int position, Instant now) {
        return new Goal(GoalId.newId(), areaId, title, description, deadline, weeklyTargetMinutes, position, now,
                List.of());
    }

    /** Rebuilds a goal from storage; {@code steps} must already be in roadmap order. */
    public static Goal restore(GoalId id, AreaId areaId, String title, String description, LocalDate deadline,
                               int position, Instant createdAt, List<RoadmapStep> steps) {
        return restore(id, areaId, title, description, deadline, null, position, createdAt, steps);
    }

    public static Goal restore(GoalId id, AreaId areaId, String title, String description, LocalDate deadline,
                               Integer weeklyTargetMinutes, int position, Instant createdAt,
                               List<RoadmapStep> steps) {
        return new Goal(id, areaId, title, description, deadline, weeklyTargetMinutes, position, createdAt, steps);
    }

    /** Keeps the weekly target as it is. */
    public void update(String title, String description, LocalDate deadline) {
        update(title, description, deadline, weeklyTargetMinutes);
    }

    /**
     * All-or-nothing: every value is validated before any field changes.
     * Deadlines in the past are allowed: a goal may be recorded late or simply overdue.
     */
    public void update(String title, String description, LocalDate deadline, Integer weeklyTargetMinutes) {
        String validTitle = Validation.requiredText(title, "Title", TITLE_MAX_LENGTH);
        String validDescription = Validation.optionalText(description, "Description", Validation.DESCRIPTION_MAX_LENGTH);
        Integer validTarget = validWeeklyTarget(weeklyTargetMinutes);
        this.title = validTitle;
        this.description = validDescription;
        this.deadline = deadline;
        this.weeklyTargetMinutes = validTarget;
    }

    /** The study time per week the learner wants to give this goal; {@code null} removes the target. */
    public void changeWeeklyTarget(Integer weeklyTargetMinutes) {
        this.weeklyTargetMinutes = validWeeklyTarget(weeklyTargetMinutes);
    }

    private static Integer validWeeklyTarget(Integer minutes) {
        if (minutes != null && (minutes < 1 || minutes > WEEKLY_TARGET_MAX_MINUTES)) {
            throw new DomainValidationException("out-of-range",
                    "Weekly target must be between 1 and " + WEEKLY_TARGET_MAX_MINUTES + ".",
                    "Weekly target", 1, WEEKLY_TARGET_MAX_MINUTES);
        }
        return minutes;
    }

    public void moveTo(int position) {
        this.position = Validation.position(position);
    }

    // ---------- roadmap ----------

    public RoadmapStep addStep(StepDetails details, StepStatus status, Instant now) {
        RoadmapStep step = RoadmapStep.create(details, status, now);
        steps.add(step);
        return step;
    }

    public void updateStep(StepId stepId, StepDetails details) {
        step(stepId).update(details);
    }

    public void changeStepStatus(StepId stepId, StepStatus status, Instant now) {
        step(stepId).changeStatus(status, now);
    }

    /** Moves the step to its next status (the click on the timeline mark) and returns it. */
    public StepStatus cycleStepStatus(StepId stepId, Instant now) {
        RoadmapStep step = step(stepId);
        step.changeStatus(step.status().next(), now);
        return step.status();
    }

    public void removeStep(StepId stepId) {
        steps.remove(step(stepId));
    }

    /** Swaps the step with its neighbour; moving past either end does nothing. */
    public void moveStep(StepId stepId, MoveDirection direction) {
        int from = steps.indexOf(step(stepId));
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (to >= 0 && to < steps.size()) {
            Collections.swap(steps, from, to);
        }
    }

    private RoadmapStep step(StepId stepId) {
        return steps.stream()
                .filter(step -> step.id().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Step " + stepId + " is not part of goal " + id));
    }

    // ---------- progress ----------

    public Progress progress() {
        return ProgressCalculator.ofSteps(steps);
    }

    public ProgressCalculator.Effort effort() {
        return ProgressCalculator.effortOf(steps);
    }

    /** Completed means there is a roadmap and every step of it is Done. */
    public boolean isCompleted() {
        return !steps.isEmpty() && steps.stream().allMatch(step -> step.status() == StepStatus.DONE);
    }

    // ---------- accessors ----------

    public GoalId id() {
        return id;
    }

    public AreaId areaId() {
        return areaId;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public LocalDate deadline() {
        return deadline;
    }

    /** Minutes per week the learner wants to give this goal, or {@code null} when there is no target. */
    public Integer weeklyTargetMinutes() {
        return weeklyTargetMinutes;
    }

    public int position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** The roadmap in order; read-only (change it through the goal). */
    public List<RoadmapStep> steps() {
        return Collections.unmodifiableList(steps);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Goal goal && id.equals(goal.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Goal[" + title + ", " + progress() + "]";
    }
}
