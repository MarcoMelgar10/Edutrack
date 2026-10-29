package com.edutrack.domain.record;

import java.util.Objects;
import java.util.UUID;

public record ExerciseId(UUID value) {

    public ExerciseId {
        Objects.requireNonNull(value, "value");
    }

    public static ExerciseId newId() {
        return new ExerciseId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
