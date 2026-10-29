package com.edutrack.domain.journal;

import java.util.Objects;
import java.util.UUID;

public record JournalEntryId(UUID value) {

    public JournalEntryId {
        Objects.requireNonNull(value, "value");
    }

    public static JournalEntryId newId() {
        return new JournalEntryId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
