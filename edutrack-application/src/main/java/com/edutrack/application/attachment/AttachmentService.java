package com.edutrack.application.attachment;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.attachment.AttachmentStorage.StoredFile;
import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.attachment.AttachmentRepository;
import com.edutrack.domain.record.RecordOwner;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Use cases for files attached to a step or a goal. A file is copied into the app's own storage, so
 * the original can be moved or deleted afterwards.
 *
 * <p>Deleting a goal or step removes the attachment <em>rows</em> through the database cascade but
 * cannot reach the files; {@link #removeOrphans()} deletes those at the next start-up.
 */
public class AttachmentService {

    private final AttachmentRepository attachments;
    private final AttachmentStorage storage;
    private final Clock clock;

    public AttachmentService(AttachmentRepository attachments, AttachmentStorage storage, Clock clock) {
        this.attachments = attachments;
        this.storage = storage;
        this.clock = clock;
    }

    /**
     * Copies the file into storage and records it for the owner. The size is checked before
     * copying, so a file over the limit never touches the disk; when saving fails after the copy,
     * the copy is deleted again.
     */
    public AttachmentView add(RecordOwner owner, Path file) {
        long size = sizeOf(file);
        Attachment.checkSize(size);
        AttachmentId id = AttachmentId.newId();
        StoredFile stored = storage.store(file, id, file.getFileName().toString());
        try {
            Attachment attachment = Attachment.create(id, owner, file.getFileName().toString(), stored.storedPath(),
                    stored.mimeType(), stored.sizeBytes(), clock.instant());
            return AttachmentView.of(attachments.save(attachment));
        } catch (RuntimeException failure) {
            storage.delete(stored.storedPath());
            throw failure;
        }
    }

    /** Files of a step or goal, newest first. */
    public List<AttachmentView> forOwner(RecordOwner owner) {
        return attachments.findByOwner(owner).stream().map(AttachmentView::of).toList();
    }

    public Optional<AttachmentView> find(AttachmentId id) {
        return attachments.findById(id).map(AttachmentView::of);
    }

    /** The file on disk, for opening it with the system's default program. */
    public Path pathOf(AttachmentId id) {
        return storage.resolve(load(id).storedPath());
    }

    /** Hands the file to another owner (evidence moved to a different step). */
    public void moveTo(AttachmentId id, RecordOwner owner) {
        Attachment attachment = load(id);
        attachment.moveTo(owner);
        attachments.save(attachment);
    }

    /** Row first, then file: a crash in between leaves an orphan file, which {@link #removeOrphans()} cleans up. */
    public void delete(AttachmentId id) {
        Attachment attachment = load(id);
        attachments.delete(id);
        storage.delete(attachment.storedPath());
    }

    /**
     * Deletes the stored files that no attachment row refers to (left behind when their goal or step
     * was deleted) and returns how many there were. Meant to run once at start-up, never while a
     * file is being added.
     */
    public int removeOrphans() {
        Set<String> known = attachments.allStoredPaths();
        int removed = 0;
        for (String path : storage.listStored()) {
            if (!known.contains(path)) {
                storage.delete(path);
                removed++;
            }
        }
        return removed;
    }

    private Attachment load(AttachmentId id) {
        return attachments.findById(id).orElseThrow(() -> new NotFoundException("Attachment", id));
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException unreadable) {
            throw new UncheckedIOException("Cannot read " + file, unreadable);
        }
    }
}
