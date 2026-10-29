package com.edutrack.application.attachment;

import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;

import java.time.Instant;

/** An attached file as the UI lists it; {@code mimeType} may be {@code null}. */
public record AttachmentView(AttachmentId id, String name, String mimeType, long sizeBytes, Instant addedAt) {

    static AttachmentView of(Attachment attachment) {
        return new AttachmentView(attachment.id(), attachment.originalName(), attachment.mimeType(),
                attachment.sizeBytes(), attachment.addedAt());
    }
}
