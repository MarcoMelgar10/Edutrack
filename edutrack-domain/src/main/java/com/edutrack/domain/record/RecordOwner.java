package com.edutrack.domain.record;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.util.Objects;
import java.util.UUID;

/**
 * What a learning record belongs to: a roadmap step (notes, resources, exercises, results)
 * or a goal (its final results). Each owner has at most one record.
 */
public record RecordOwner(Type type, UUID id) {

    public enum Type {
        GOAL,
        STEP
    }

    public RecordOwner {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(id, "id");
    }

    public static RecordOwner ofGoal(GoalId goalId) {
        return new RecordOwner(Type.GOAL, goalId.value());
    }

    public static RecordOwner ofStep(StepId stepId) {
        return new RecordOwner(Type.STEP, stepId.value());
    }

    public boolean isStep() {
        return type == Type.STEP;
    }
}
