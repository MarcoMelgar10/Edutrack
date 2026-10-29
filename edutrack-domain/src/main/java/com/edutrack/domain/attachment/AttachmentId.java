package com.edutrack.domain.attachment;

import java.util.Objects;
import java.util.UUID;

public record AttachmentId(UUID value) {

    public AttachmentId {
        Objects.requireNonNull(value, "value");
    }

    public static AttachmentId newId() {
        return new AttachmentId(UUID.randomUUID());
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
