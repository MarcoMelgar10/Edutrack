package com.edutrack.domain.skill;

import java.util.Objects;
import java.util.UUID;

public record CompetencyId(UUID value) {

    public CompetencyId {
        Objects.requireNonNull(value, "value");
    }

    public static CompetencyId newId() {
        return new CompetencyId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
