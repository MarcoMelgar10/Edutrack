package com.edutrack.application.attachment;

import com.edutrack.domain.attachment.AttachmentId;

import java.nio.file.Path;
import java.util.Set;

/**
 * Port for keeping attached files on disk, away from the database. Paths handed in and out are
 * relative to the storage's own root and use {@code /} separators.
 */
public interface AttachmentStorage {

    /** Copies the file (the original is never moved or changed) and says where and how big the copy is. */
    StoredFile store(Path source, AttachmentId id, String originalName);

    /** The absolute location of a stored file, for opening it. */
    Path resolve(String storedPath);

    /** Deletes a stored file; a file that is already gone is fine. */
    void delete(String storedPath);

    /** Every file currently kept, to find the ones no row refers to. */
    Set<String> listStored();

    /** {@code mimeType} is {@code null} when it could not be determined. */
    record StoredFile(String storedPath, long sizeBytes, String mimeType) {
    }
}
