package com.edutrack.domain.review;

import java.util.Objects;
import java.util.UUID;

public record ReviewLogId(UUID value) {

    public ReviewLogId {
        Objects.requireNonNull(value, "value");
    }

    public static ReviewLogId newId() {
        return new ReviewLogId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
