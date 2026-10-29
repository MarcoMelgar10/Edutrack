package com.edutrack.desktop.startup;

import com.edutrack.application.backup.BackupArchive;
import com.edutrack.application.backup.BackupInfo;
import com.edutrack.application.backup.BackupService;
import com.edutrack.desktop.ui.settings.UiPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

/** Fake archive, fixed clock, temporary folder and a preferences node of its own: nothing real is touched. */
class AutoBackupTest {

    private static final Instant NOW = Instant.parse("2026-10-28T14:05:00Z");

    @TempDir
    Path folder;

    private final Preferences node = Preferences.userRoot().node("edutrack-test/" + UUID.randomUUID());
    private final UiPreferences preferences = new UiPreferences(node);
    private boolean fail;
    private final BackupArchive archive = new BackupArchive() {
        @Override
        public BackupInfo write(Path zip) {
            if (fail) {
                throw new IllegalStateException("disk full");
            }
            try {
                Files.writeString(zip, "backup");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new BackupInfo("9", NOW, Map.of(), 0);
        }

        @Override
        public BackupInfo read(Path zip) {
            return new BackupInfo("9", NOW, Map.of(), 0);
        }

        @Override
        public void restore(Path zip) {
        }
    };
    private final AutoBackup autoBackup = new AutoBackup(
            new BackupService(archive, Clock.fixed(NOW, ZoneOffset.UTC)), preferences, Runnable::run);

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
    }

    private void enabledIn(boolean enabled) {
        preferences.setBackupFolder(folder.toString());
        preferences.setAutomaticWeeklyBackup(enabled);
    }

    private long backupFiles() throws IOException {
        try (var files = Files.list(folder)) {
            return files.count();
        }
    }

    @Test
    void withNoBackupYetOneIsMade() throws IOException {
        enabledIn(true);

        autoBackup.runInBackground();

        assertThat(backupFiles()).isEqualTo(1);
    }

    @Test
    void aBackupOlderThanAWeekIsReplacedByANewOne() throws IOException {
        enabledIn(true);
        Files.writeString(folder.resolve("EduTrack-backup-2026-10-20-1400.zip"), "old"); // 8 days before

        autoBackup.runInBackground();

        assertThat(backupFiles()).isEqualTo(2);
        assertThat(folder.resolve("EduTrack-backup-2026-10-28-1405.zip")).exists();
    }

    @Test
    void aBackupFromThisWeekIsLeftAlone() throws IOException {
        enabledIn(true);
        Files.writeString(folder.resolve("EduTrack-backup-2026-10-24-1400.zip"), "recent"); // 4 days before

        autoBackup.runInBackground();

        assertThat(backupFiles()).isEqualTo(1);
    }

    @Test
    void nothingIsDoneWhenTheSettingIsOff() throws IOException {
        enabledIn(false);

        autoBackup.runInBackground();

        assertThat(backupFiles()).isZero();
    }

    @Test
    void aFailureIsSwallowedSoTheAppStillStarts() throws IOException {
        enabledIn(true);
        fail = true;

        autoBackup.runInBackground();

        assertThat(autoBackup.runNow()).isEmpty();
        assertThat(backupFiles()).isZero();
    }
}
