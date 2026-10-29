package com.edutrack.domain.goal;

import java.util.Objects;
import java.util.UUID;

public record GoalId(UUID value) {

    public GoalId {
        Objects.requireNonNull(value, "value");
    }

    public static GoalId newId() {
        return new GoalId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
