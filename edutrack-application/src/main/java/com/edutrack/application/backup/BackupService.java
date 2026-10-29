package com.edutrack.application.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Use cases for backing up and restoring all data. Backups are named after the minute they were
 * made ({@code EduTrack-backup-2026-10-28-1405.zip}), so the age of the newest one is read from
 * the file names and no other file in the folder is ever looked at or deleted.
 */
public class BackupService {

    /** An automatic backup is made when the newest one is at least this old. */
    public static final Duration AUTO_BACKUP_INTERVAL = Duration.ofDays(7);
    /** Automatic backups kept in the folder; older ones are deleted. */
    public static final int AUTO_BACKUPS_KEPT = 8;

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm");
    private static final Pattern BACKUP_NAME = Pattern.compile("EduTrack-backup-(\\d{4}-\\d{2}-\\d{2}-\\d{4})\\.zip");

    private final BackupArchive archive;
    private final Clock clock;

    public BackupService(BackupArchive archive, Clock clock) {
        this.archive = archive;
        this.clock = clock;
    }

    /** Writes a new backup into {@code folder} (created when missing) and returns its path. */
    public Path backupTo(Path folder) {
        Path zip = folder.resolve("EduTrack-backup-" + STAMP.format(LocalDateTime.now(clock)) + ".zip");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create " + folder, e);
        }
        archive.write(zip);
        return zip;
    }

    /** What the backup contains, without restoring it. */
    public BackupInfo inspect(Path zip) {
        return archive.read(zip);
    }

    /** Replaces all current data with the backup's. */
    public void restore(Path zip) {
        archive.restore(zip);
    }

    /**
     * Makes a backup when {@code enabled} and the newest one in the folder is a week old or there is none,
     * then keeps only the newest {@value #AUTO_BACKUPS_KEPT}. Returns the new file, or empty when nothing was due.
     */
    public Optional<Path> autoBackupIfDue(Path folder, boolean enabled) {
        if (!enabled) {
            return Optional.empty();
        }
        Optional<Instant> last = lastBackup(folder);
        if (last.isPresent() && clock.instant().isBefore(last.get().plus(AUTO_BACKUP_INTERVAL))) {
            return Optional.empty();
        }
        Path created = backupTo(folder);
        deleteOldBackups(folder);
        return Optional.of(created);
    }

    /** When the newest backup in the folder was made, or empty when there is none. */
    public Optional<Instant> lastBackup(Path folder) {
        return backupsNewestFirst(folder).stream().findFirst().map(Backup::madeAt);
    }

    /** The backups in the folder, newest first; other files are ignored. Empty when the folder does not exist. */
    public List<Backup> backups(Path folder) {
        return backupsNewestFirst(folder);
    }

    private void deleteOldBackups(Path folder) {
        List<Backup> backups = backupsNewestFirst(folder);
        for (Backup old : backups.subList(Math.min(AUTO_BACKUPS_KEPT, backups.size()), backups.size())) {
            try {
                Files.deleteIfExists(old.file());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot delete " + old.file(), e);
            }
        }
    }

    private List<Backup> backupsNewestFirst(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(Files::isRegularFile)
                    .map(this::asBackup)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(Backup::madeAt).reversed())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot list " + folder, e);
        }
    }

    private Optional<Backup> asBackup(Path file) {
        Matcher name = BACKUP_NAME.matcher(file.getFileName().toString());
        if (!name.matches()) {
            return Optional.empty();
        }
        try {
            Instant madeAt = LocalDateTime.parse(name.group(1), STAMP).atZone(clock.getZone()).toInstant();
            return Optional.of(new Backup(file, madeAt));
        } catch (DateTimeParseException notADate) {
            return Optional.empty();
        }
    }

    /** A backup file in the folder and the minute it was made. */
    public record Backup(Path file, Instant madeAt) {
    }
}
