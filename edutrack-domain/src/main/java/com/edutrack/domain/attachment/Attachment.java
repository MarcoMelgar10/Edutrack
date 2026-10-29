package com.edutrack.domain.attachment;

import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root: a file the learner attached to a step or a goal (a certificate, a recording, an
 * essay…). Only the metadata lives here; the bytes are kept by the application's file storage under
 * {@link #storedPath()}. Immutable apart from the owner, which follows a piece of evidence moved to
 * another step.
 */
public final class Attachment {

    public static final int NAME_MAX_LENGTH = 255;
    public static final int PATH_MAX_LENGTH = 400;
    public static final int MIME_MAX_LENGTH = 120;
    public static final long MAX_SIZE_MB = 200;
    public static final long MAX_SIZE_BYTES = MAX_SIZE_MB * 1024 * 1024;

    private final AttachmentId id;
    private RecordOwner owner;
    private final String originalName;
    private final String storedPath;
    private final String mimeType;
    private final long sizeBytes;
    private final Instant addedAt;

    private Attachment(AttachmentId id, RecordOwner owner, String originalName, String storedPath,
                       String mimeType, long sizeBytes, Instant addedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.originalName = Validation.requiredText(originalName, "File name", NAME_MAX_LENGTH);
        this.storedPath = Validation.requiredText(storedPath, "Stored path", PATH_MAX_LENGTH);
        this.mimeType = Validation.optionalText(mimeType, "File type", MIME_MAX_LENGTH);
        this.sizeBytes = checkSize(sizeBytes);
        this.addedAt = Objects.requireNonNull(addedAt, "addedAt");
    }

    /**
     * The id is chosen by the caller because the stored path is derived from it, so the file has to be
     * copied before the attachment can be built.
     */
    public static Attachment create(AttachmentId id, RecordOwner owner, String originalName, String storedPath,
                                    String mimeType, long sizeBytes, Instant now) {
        return new Attachment(id, owner, originalName, storedPath, mimeType, sizeBytes, now);
    }

    public static Attachment restore(AttachmentId id, RecordOwner owner, String originalName, String storedPath,
                                     String mimeType, long sizeBytes, Instant addedAt) {
        return new Attachment(id, owner, originalName, storedPath, mimeType, sizeBytes, addedAt);
    }

    /** The size rule alone, so callers can reject a file before copying it. */
    public static long checkSize(long sizeBytes) {
        if (sizeBytes < 1) {
            throw new DomainValidationException("not-positive", "File size must be greater than 0.", "File size");
        }
        if (sizeBytes > MAX_SIZE_BYTES) {
            throw new DomainValidationException("too-large", "File must be at most " + MAX_SIZE_MB + " MB.",
                    "File", MAX_SIZE_MB);
        }
        return sizeBytes;
    }

    public void moveTo(RecordOwner newOwner) {
        this.owner = Objects.requireNonNull(newOwner, "owner");
    }

    public AttachmentId id() {
        return id;
    }

    public RecordOwner owner() {
        return owner;
    }

    public String originalName() {
        return originalName;
    }

    /** Path relative to the files root, always with {@code /} separators. */
    public String storedPath() {
        return storedPath;
    }

    /** May be {@code null} when the type could not be determined. */
    public String mimeType() {
        return mimeType;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public Instant addedAt() {
        return addedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Attachment attachment && id.equals(attachment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
