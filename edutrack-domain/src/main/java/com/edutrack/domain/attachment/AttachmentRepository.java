package com.edutrack.domain.attachment;

import com.edutrack.domain.record.RecordOwner;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Port for storing attachment rows (the files themselves live behind the application's storage port). */
public interface AttachmentRepository {

    Attachment save(Attachment attachment);

    Optional<Attachment> findById(AttachmentId id);

    void delete(AttachmentId id);

    /** Files attached to a step or a goal, newest first. */
    List<Attachment> findByOwner(RecordOwner owner);

    /** Every stored path that has a row, to find files left behind by deleted goals and steps. */
    Set<String> allStoredPaths();
}
