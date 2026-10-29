package com.edutrack.desktop.ui.settings;

import com.edutrack.application.backup.BackupArchive;
import com.edutrack.application.backup.BackupInfo;
import com.edutrack.application.backup.BackupService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs on a fake archive, a temporary folder and a preferences node of its own. */
class BackupViewModelTest {

    private static final Instant NOW = Instant.parse("2026-10-28T14:05:30Z");

    @TempDir
    Path folder;

    private final Preferences node = Preferences.userRoot().node("edutrack-test/" + UUID.randomUUID());
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FakeArchive archive = new FakeArchive();
    private final DataChanges dataChanges = new DataChanges();
    private final UiPreferences preferences = new UiPreferences(node);
    private BackupViewModel viewModel;
    private long changes;

    private static final class FakeArchive implements BackupArchive {

        final List<Path> restored = new ArrayList<>();
        RuntimeException failure;

        @Override
        public BackupInfo write(Path zip) {
            if (failure != null) {
                throw failure;
            }
            try {
                Files.writeString(zip, "backup");
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            return new BackupInfo("9", NOW, Map.of("goal", 2), 1);
        }

        @Override
        public BackupInfo read(Path zip) {
            if (failure != null) {
                throw failure;
            }
            return new BackupInfo("9", NOW, Map.of("goal", 2, "step", 3), 1);
        }

        @Override
        public void restore(Path zip) {
            if (failure != null) {
                throw failure;
            }
            restored.add(zip);
        }
    }

    private BackupViewModel viewModel() {
        preferences.setBackupFolder(folder.toString());
        dataChanges.subscribe(() -> changes++);
        viewModel = new BackupViewModel(new BackupService(archive, clock), preferences, dataChanges, new Messages(),
                clock, Runnable::run, Runnable::run);
        return viewModel;
    }

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void backingUpWritesAFileListsItAndSaysSo() {
        BackupViewModel vm = viewModel();

        vm.backUpNow();

        assertThat(vm.failedProperty().get()).isFalse();
        assertThat(vm.messageProperty().get()).isEqualTo("Backup saved: EduTrack-backup-2026-10-28-1405.zip");
        assertThat(vm.backups()).hasSize(1);
        assertThat(vm.backups().get(0).size()).isEqualTo("6 B");
        assertThat(vm.backups().get(0).madeAt()).contains("14:05");
        assertThat(vm.busyProperty().get()).isFalse();
    }

    @Test
    void theListShowsTheNewestBackupFirstAndIgnoresOtherFiles() throws IOException {
        Files.writeString(folder.resolve("EduTrack-backup-2026-10-01-0900.zip"), "a");
        Files.writeString(folder.resolve("EduTrack-backup-2026-10-20-0900.zip"), "bb");
        Files.writeString(folder.resolve("holiday.zip"), "c");
        BackupViewModel vm = viewModel();

        vm.refresh();

        assertThat(vm.backups()).extracting(entry -> entry.file().getFileName().toString())
                .containsExactly("EduTrack-backup-2026-10-20-0900.zip", "EduTrack-backup-2026-10-01-0900.zip");
    }

    @Test
    void aMissingFolderIsAnEmptyListNotAnError() {
        BackupViewModel vm = viewModel();
        preferences.setBackupFolder(folder.resolve("nowhere").toString());

        vm.refresh();

        assertThat(vm.backups()).isEmpty();
        assertThat(vm.failedProperty().get()).isFalse();
    }

    @Test
    void aFailedBackupBecomesAMessageNotAnException() {
        archive.failure = new IllegalStateException("disk full");
        BackupViewModel vm = viewModel();

        vm.backUpNow();

        assertThat(vm.failedProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).isEqualTo("The backup could not be made. disk full");
        assertThat(vm.busyProperty().get()).isFalse();
    }

    @Test
    void restoringReplacesTheDataAndTellsTheRestOfTheApp() throws IOException {
        Path zip = Files.writeString(folder.resolve("EduTrack-backup-2026-10-20-0900.zip"), "x");
        BackupViewModel vm = viewModel();

        vm.restore(zip);

        assertThat(archive.restored).containsExactly(zip);
        assertThat(vm.messageProperty().get()).isEqualTo("Data restored from EduTrack-backup-2026-10-20-0900.zip.");
        assertThat(vm.failedProperty().get()).isFalse();
        assertThat(changes).isEqualTo(1);
    }

    @Test
    void aFailedRestoreIsReportedAndDoesNotSignalAChange() {
        archive.failure = new DomainValidationException("backup-invalid", "not a backup");
        BackupViewModel vm = viewModel();

        vm.restore(folder.resolve("whatever.zip"));

        assertThat(vm.failedProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).isEqualTo("The backup could not be restored. This file is not an EduTrack backup.");
        assertThat(changes).isZero();
    }

    @Test
    void inspectingGivesTheContentsOrTheReason() {
        BackupViewModel vm = viewModel();

        assertThat(vm.inspect(folder.resolve("a.zip"))).hasValueSatisfying(info -> {
            assertThat(info.rows()).isEqualTo(5);
            assertThat(info.files()).isEqualTo(1);
        });

        archive.failure = new DomainValidationException("backup-newer", "newer");
        assertThat(vm.inspect(folder.resolve("a.zip"))).isEmpty();
        assertThat(vm.failedProperty().get()).isTrue();
        assertThat(vm.messageProperty().get()).contains("newer version of EduTrack");
    }
}
