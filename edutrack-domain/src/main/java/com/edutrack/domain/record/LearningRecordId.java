package com.edutrack.domain.record;

import java.util.Objects;
import java.util.UUID;

public record LearningRecordId(UUID value) {

    public LearningRecordId {
        Objects.requireNonNull(value, "value");
    }

    public static LearningRecordId newId() {
        return new LearningRecordId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
