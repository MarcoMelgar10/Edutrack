package com.edutrack.domain.evidence;

import java.util.Objects;
import java.util.UUID;

public record EvidenceId(UUID value) {

    public EvidenceId {
        Objects.requireNonNull(value, "value");
    }

    public static EvidenceId newId() {
        return new EvidenceId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
