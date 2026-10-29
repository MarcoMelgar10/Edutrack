package com.edutrack.domain.session;

import java.util.Objects;
import java.util.UUID;

public record StudySessionId(UUID value) {

    public StudySessionId {
        Objects.requireNonNull(value, "value");
    }

    public static StudySessionId newId() {
        return new StudySessionId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
