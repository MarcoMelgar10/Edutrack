package com.edutrack.application.backup;

import com.edutrack.application.support.MutableClock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BackupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T14:05:30Z");

    @TempDir
    Path folder;

    private final MutableClock clock = new MutableClock(NOW, ZoneOffset.UTC);
    private final FakeArchive archive = new FakeArchive();
    private final BackupService service = new BackupService(archive, clock);

    /** Writes a small file where a real archive would write a zip, and remembers what it was asked. */
    private static final class FakeArchive implements BackupArchive {

        final List<Path> written = new ArrayList<>();
        final List<Path> restored = new ArrayList<>();
        final BackupInfo info = new BackupInfo("9", NOW, Map.of("goal", 2), 1);

        @Override
        public BackupInfo write(Path zip) {
            try {
                Files.writeString(zip, "backup");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            written.add(zip);
            return info;
        }

        @Override
        public BackupInfo read(Path zip) {
            return info;
        }

        @Override
        public void restore(Path zip) {
            restored.add(zip);
        }
    }

    private Path existing(String name) throws IOException {
        return Files.writeString(folder.resolve(name), "old");
    }

    private List<String> names() throws IOException {
        try (Stream<Path> files = Files.list(folder)) {
            return files.map(file -> file.getFileName().toString()).sorted().toList();
        }
    }

    @Test
    void backupIsNamedAfterTheMinuteItWasMade() {
        Path zip = service.backupTo(folder);

        assertThat(zip).isEqualTo(folder.resolve("EduTrack-backup-2026-10-28-1405.zip"));
        assertThat(zip).exists();
    }

    @Test
    void backupCreatesTheFolderWhenMissing() {
        Path nested = folder.resolve("a").resolve("b");

        Path zip = service.backupTo(nested);

        assertThat(zip.getParent()).isEqualTo(nested);
        assertThat(zip).exists();
    }

    @Test
    void inspectAndRestoreGoStraightToTheArchive() {
        Path zip = folder.resolve("x.zip");

        assertThat(service.inspect(zip)).isSameAs(archive.info);
        service.restore(zip);

        assertThat(archive.restored).containsExactly(zip);
    }

    @Test
    void lastBackupIsTheNewestByNameAndIgnoresOtherFiles() throws IOException {
        existing("EduTrack-backup-2026-10-01-0900.zip");
        existing("EduTrack-backup-2026-10-20-2359.zip");
        existing("EduTrack-backup-2026-10-10-1000.zip");
        existing("holiday-photos.zip");
        existing("EduTrack-backup-notes.txt");
        existing("EduTrack-backup-2026-13-45-9999.zip");

        assertThat(service.lastBackup(folder)).contains(Instant.parse("2026-10-20T23:59:00Z"));
    }

    @Test
    void lastBackupIsEmptyForAMissingOrEmptyFolder() {
        assertThat(service.lastBackup(folder)).isEmpty();
        assertThat(service.lastBackup(folder.resolve("nowhere"))).isEmpty();
    }

    @Test
    void autoBackupDoesNothingWhenDisabled() {
        assertThat(service.autoBackupIfDue(folder, false)).isEmpty();
        assertThat(archive.written).isEmpty();
    }

    @Test
    void autoBackupWritesWhenThereIsNoBackupYet() {
        Optional<Path> created = service.autoBackupIfDue(folder, true);

        assertThat(created).contains(folder.resolve("EduTrack-backup-2026-10-28-1405.zip"));
        assertThat(archive.written).hasSize(1);
    }

    @Test
    void autoBackupWaitsUntilTheNewestIsAWeekOld() throws IOException {
        existing("EduTrack-backup-2026-10-21-1406.zip");

        assertThat(service.autoBackupIfDue(folder, true)).as("30 seconds short of a week").isEmpty();

        clock.advance(Duration.ofSeconds(30));
        assertThat(service.autoBackupIfDue(folder, true)).as("exactly a week").isPresent();
        assertThat(archive.written).hasSize(1);
    }

    @Test
    void autoBackupKeepsTheNewestEightAndNeverTouchesOtherFiles() throws IOException {
        for (int day = 1; day <= 9; day++) {
            existing("EduTrack-backup-2026-09-%02d-1000.zip".formatted(day));
        }
        existing("holiday-photos.zip");
        existing("notes.txt");
        existing("EduTrack-backup-extra.zip");

        Optional<Path> created = service.autoBackupIfDue(folder, true);

        assertThat(created).isPresent();
        assertThat(names()).containsExactly(
                "EduTrack-backup-2026-09-03-1000.zip",
                "EduTrack-backup-2026-09-04-1000.zip",
                "EduTrack-backup-2026-09-05-1000.zip",
                "EduTrack-backup-2026-09-06-1000.zip",
                "EduTrack-backup-2026-09-07-1000.zip",
                "EduTrack-backup-2026-09-08-1000.zip",
                "EduTrack-backup-2026-09-09-1000.zip",
                "EduTrack-backup-2026-10-28-1405.zip",
                "EduTrack-backup-extra.zip",
                "holiday-photos.zip",
                "notes.txt");
    }

    @Test
    void aManualBackupNeverDeletesAnything() throws IOException {
        for (int day = 1; day <= 9; day++) {
            existing("EduTrack-backup-2026-09-%02d-1000.zip".formatted(day));
        }

        service.backupTo(folder);

        assertThat(names()).hasSize(10);
    }
}
