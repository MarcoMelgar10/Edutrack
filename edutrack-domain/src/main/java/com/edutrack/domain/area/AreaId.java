package com.edutrack.domain.area;

import java.util.Objects;
import java.util.UUID;

public record AreaId(UUID value) {

    public AreaId {
        Objects.requireNonNull(value, "value");
    }

    public static AreaId newId() {
        return new AreaId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
