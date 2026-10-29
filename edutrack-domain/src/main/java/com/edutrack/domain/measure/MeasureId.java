package com.edutrack.domain.measure;

import java.util.Objects;
import java.util.UUID;

public record MeasureId(UUID value) {

    public MeasureId {
        Objects.requireNonNull(value, "value");
    }

    public static MeasureId newId() {
        return new MeasureId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
