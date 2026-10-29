package com.edutrack.application.backup;

import java.nio.file.Path;

/**
 * Port for a complete copy of the learner's data in one {@code .zip} file. Implemented by the
 * infrastructure layer, which knows the database and where the attached files live.
 *
 * <p>Failures that the user can act on are reported as {@code DomainValidationException}s with the
 * codes {@code backup-invalid} (not a backup, or damaged) and {@code backup-newer} (made by a newer
 * version of the app than this database).
 */
public interface BackupArchive {

    /** Writes every table and attached file to {@code zip} and says what it contains. */
    BackupInfo write(Path zip);

    /** Reads only the manifest of a backup, without touching the database. */
    BackupInfo read(Path zip);

    /** Replaces all current data and files with the ones in the backup, all or nothing. */
    void restore(Path zip);
}
