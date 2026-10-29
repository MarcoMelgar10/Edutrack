package com.edutrack.domain.review;

import java.util.Objects;
import java.util.UUID;

public record CardId(UUID value) {

    public CardId {
        Objects.requireNonNull(value, "value");
    }

    public static CardId newId() {
        return new CardId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
