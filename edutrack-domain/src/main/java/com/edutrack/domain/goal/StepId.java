package com.edutrack.domain.goal;

import java.util.Objects;
import java.util.UUID;

public record StepId(UUID value) {

    public StepId {
        Objects.requireNonNull(value, "value");
    }

    public static StepId newId() {
        return new StepId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
