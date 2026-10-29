package com.edutrack.domain.practice;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root: something the learner practises on a rhythm ("Shadowing, 20 min, 5 times a week"),
 * for a goal and optionally one of its steps. It is the open-ended counterpart of a roadmap step:
 * it is never finished, only paused. The goal and step are referenced by id only.
 */
public final class PracticeRoutine {

    public static final int TITLE_MAX_LENGTH = 120;
    public static final int DAYS_PER_WEEK = 7;
    public static final int MAX_TARGET_MINUTES = 600;

    private final PracticeRoutineId id;
    private final GoalId goalId;
    private final Instant createdAt;
    private StepId stepId;
    private String title;
    private Cadence cadence;
    private int timesPerWeek;
    private int targetMinutes;
    private boolean active = true;
    private int position;

    private PracticeRoutine(PracticeRoutineId id, GoalId goalId, RoutineDetails details, boolean active,
                            int position, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.active = active;
        update(details);
        moveTo(position);
    }

    public static PracticeRoutine create(GoalId goalId, RoutineDetails details, int position, Instant now) {
        return new PracticeRoutine(PracticeRoutineId.newId(), goalId, details, true, position, now);
    }

    /** Rebuilds a routine from storage. */
    public static PracticeRoutine restore(PracticeRoutineId id, GoalId goalId, StepId stepId, String title,
                                          Cadence cadence, int timesPerWeek, int targetMinutes, boolean active,
                                          int position, Instant createdAt) {
        return new PracticeRoutine(id, goalId,
                new RoutineDetails(stepId, title, cadence, timesPerWeek, targetMinutes), active, position, createdAt);
    }

    /** All-or-nothing: every value is validated before any field changes. */
    public void update(RoutineDetails details) {
        Objects.requireNonNull(details, "details");
        String validTitle = Validation.requiredText(details.title(), "Title", TITLE_MAX_LENGTH);
        Cadence validCadence = Validation.required(details.cadence(), "Cadence");
        int validTimes = validCadence == Cadence.DAILY
                ? DAYS_PER_WEEK
                : inRange(Validation.required(details.timesPerWeek(), "Times per week"), "Times per week", 1, DAYS_PER_WEEK);
        int validMinutes = inRange(Validation.required(details.targetMinutes(), "Target minutes"),
                "Target minutes", 1, MAX_TARGET_MINUTES);
        this.stepId = details.stepId();
        this.title = validTitle;
        this.cadence = validCadence;
        this.timesPerWeek = validTimes;
        this.targetMinutes = validMinutes;
    }

    private static int inRange(int value, String field, int min, int max) {
        if (value < min || value > max) {
            throw new DomainValidationException("out-of-range",
                    field + " must be between " + min + " and " + max + ".", field, min, max);
        }
        return value;
    }

    public void pause() {
        active = false;
    }

    public void resume() {
        active = true;
    }

    public void moveTo(int position) {
        this.position = Validation.position(position);
    }

    public PracticeRoutineId id() {
        return id;
    }

    public GoalId goalId() {
        return goalId;
    }

    public StepId stepId() {
        return stepId;
    }

    public String title() {
        return title;
    }

    public Cadence cadence() {
        return cadence;
    }

    /** Days per week the routine is meant to be practised; always 7 for a daily routine. */
    public int timesPerWeek() {
        return timesPerWeek;
    }

    public int targetMinutes() {
        return targetMinutes;
    }

    public boolean active() {
        return active;
    }

    public int position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PracticeRoutine routine && id.equals(routine.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "PracticeRoutine[" + title + ", " + timesPerWeek + "x/week]";
    }
}
