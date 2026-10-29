package com.edutrack.domain.practice;

import java.util.Objects;
import java.util.UUID;

public record PracticeRoutineId(UUID value) {

    public PracticeRoutineId {
        Objects.requireNonNull(value, "value");
    }

    public static PracticeRoutineId newId() {
        return new PracticeRoutineId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
