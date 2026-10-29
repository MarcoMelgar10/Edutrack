package com.edutrack.domain.record;

import java.util.Objects;
import java.util.UUID;

public record AssessmentId(UUID value) {

    public AssessmentId {
        Objects.requireNonNull(value, "value");
    }

    public static AssessmentId newId() {
        return new AssessmentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
