package com.edutrack.domain.skill;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Aggregate root: something a goal is meant to build (Listening, SQL, Negotiation) with a
 * self-assessed level from 0 to 5, the level wanted and the roadmap steps that contribute to it.
 *
 * <p>Every level change is dated, so the history can be charted. The labels of the levels
 * (Not started … Mastered) are a presentation concern.
 */
public final class Competency {

    public static final int NAME_MAX_LENGTH = 80;
    public static final int MIN_LEVEL = 0;
    public static final int MAX_LEVEL = 5;

    private final CompetencyId id;
    private final GoalId goalId;
    private final Instant createdAt;
    private final List<LevelChange> history;
    private final Set<StepId> steps;
    private String name;
    private String description;
    private int level;
    private int targetLevel;
    private int position;

    private Competency(CompetencyId id, GoalId goalId, CompetencyDetails details, int level, int position,
                       Instant createdAt, List<LevelChange> history, Set<StepId> steps) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.level = validRange(level, MIN_LEVEL, "Level");
        this.history = new ArrayList<>(history);
        this.history.sort(Comparator.comparing(LevelChange::on));
        this.steps = new LinkedHashSet<>(steps);
        update(details);
        moveTo(position);
    }

    /** A new competency starts at level 0, with no history. */
    public static Competency create(GoalId goalId, CompetencyDetails details, int position, Instant now) {
        return new Competency(CompetencyId.newId(), goalId, details, MIN_LEVEL, position, now, List.of(), Set.of());
    }

    /** Rebuilds a competency from storage; the history is put in date order. */
    public static Competency restore(CompetencyId id, GoalId goalId, CompetencyDetails details, int level,
                                     int position, Instant createdAt, List<LevelChange> history, Set<StepId> steps) {
        return new Competency(id, goalId, details, level, position, createdAt, history, steps);
    }

    /** All-or-nothing: every value is validated before any field changes. */
    public void update(CompetencyDetails details) {
        Objects.requireNonNull(details, "details");
        String validName = Validation.requiredText(details.name(), "Name", NAME_MAX_LENGTH);
        String validDescription = Validation.optionalText(details.description(), "Description", Validation.DESCRIPTION_MAX_LENGTH);
        int validTarget = validRange(Validation.required(details.targetLevel(), "Target level"), 1, "Target level");
        this.name = validName;
        this.description = validDescription;
        this.targetLevel = validTarget;
    }

    /**
     * Sets the current level and records it in the history. Changing it again on the same day
     * replaces that day's entry (the learner corrected themselves); setting the same level does nothing.
     */
    public void changeLevel(int newLevel, LocalDate today) {
        int valid = validRange(newLevel, MIN_LEVEL, "Level");
        Objects.requireNonNull(today, "today");
        if (valid == level) {
            return;
        }
        level = valid;
        history.removeIf(change -> change.on().equals(today));
        history.add(new LevelChange(today, valid));
        history.sort(Comparator.comparing(LevelChange::on));
    }

    /** Adds the steps to the ones already linked. */
    public void linkSteps(Set<StepId> stepIds) {
        steps.addAll(stepIds);
    }

    /** Replaces the linked steps with exactly these. */
    public void setSteps(Set<StepId> stepIds) {
        Set<StepId> replacement = new LinkedHashSet<>(stepIds);
        steps.clear();
        steps.addAll(replacement);
    }

    public void unlinkStep(StepId stepId) {
        steps.remove(stepId);
    }

    public void moveTo(int position) {
        this.position = Validation.position(position);
    }

    /** Whether the current level has reached the wanted one. */
    public boolean meetsTarget() {
        return level >= targetLevel;
    }

    private static int validRange(int value, int min, String field) {
        if (value < min || value > MAX_LEVEL) {
            throw new DomainValidationException("out-of-range",
                    field + " must be between " + min + " and " + MAX_LEVEL + ".", field, min, MAX_LEVEL);
        }
        return value;
    }

    // ---------- accessors ----------

    public CompetencyId id() {
        return id;
    }

    public GoalId goalId() {
        return goalId;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public int level() {
        return level;
    }

    public int targetLevel() {
        return targetLevel;
    }

    public int position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** Every level change, oldest first; read-only. */
    public List<LevelChange> history() {
        return Collections.unmodifiableList(history);
    }

    /** The roadmap steps that contribute to this competency; read-only. */
    public Set<StepId> steps() {
        return Collections.unmodifiableSet(steps);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Competency competency && id.equals(competency.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Competency[" + name + ", " + level + "/" + targetLevel + "]";
    }
}
