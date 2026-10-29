package com.edutrack.domain.record;

import java.util.Objects;
import java.util.UUID;

public record ResourceId(UUID value) {

    public ResourceId {
        Objects.requireNonNull(value, "value");
    }

    public static ResourceId newId() {
        return new ResourceId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
