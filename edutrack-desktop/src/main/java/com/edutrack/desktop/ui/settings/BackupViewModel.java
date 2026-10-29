package com.edutrack.desktop.ui.settings;

import com.edutrack.application.backup.BackupInfo;
import com.edutrack.application.backup.BackupService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.files.FilesViewModel;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * State of the Data section of Settings: back up now, the list of backups in the backup folder and
 * restoring one. The slow work runs off the FX thread; every failure ends up as a message, never an exception.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BackupViewModel {

    /** One backup in the list: its file, the day and time it was made, and its size. */
    public record Entry(Path file, String madeAt, String size) {
    }

    private final BackupService backupService;
    private final UiPreferences preferences;
    private final DataChanges dataChanges;
    private final Messages messages;
    private final Clock clock;
    private final Executor background;
    private final Executor ui;

    private final ObservableList<Entry> backups = FXCollections.observableArrayList();
    private final ObservableList<Entry> backupsReadOnly = FXCollections.unmodifiableObservableList(backups);
    private final ReadOnlyStringWrapper message = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper failed = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper();

    @Autowired
    public BackupViewModel(BackupService backupService, UiPreferences preferences, DataChanges dataChanges,
                           Messages messages, Clock clock) {
        this(backupService, preferences, dataChanges, messages, clock, command -> {
            Thread worker = new Thread(command, "backup");
            worker.setDaemon(true);
            worker.start();
        }, Platform::runLater);
    }

    /** For tests: who runs the slow work and who applies its result. */
    BackupViewModel(BackupService backupService, UiPreferences preferences, DataChanges dataChanges,
                    Messages messages, Clock clock, Executor background, Executor ui) {
        this.backupService = backupService;
        this.preferences = preferences;
        this.dataChanges = dataChanges;
        this.messages = messages;
        this.clock = clock;
        this.background = background;
        this.ui = ui;
    }

    public Path folder() {
        return Path.of(preferences.getBackupFolder());
    }

    /** Reads the backup folder again into {@link #backups()}, newest first. */
    public void refresh() {
        try {
            backups.setAll(backupService.backups(folder()).stream()
                    .map(backup -> new Entry(backup.file(),
                            Formats.date(backup.madeAt(), clock.getZone()) + " "
                                    + Formats.time(backup.madeAt(), clock.getZone()),
                            FilesViewModel.size(sizeOf(backup.file()))))
                    .toList());
        } catch (RuntimeException failure) {
            backups.clear();
            fail("settings.data.listFailed", failure);
        }
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException unreadable) {
            return 0;
        }
    }

    /** Writes a backup into the backup folder. */
    public void backUpNow() {
        run(() -> backupService.backupTo(folder()), file -> {
            refresh();
            succeed(messages.get("settings.data.backupDone", file.getFileName()));
        }, "settings.data.backupFailed");
    }

    /** What a backup file holds, for the confirmation; empty (with the reason as message) when it cannot be read. */
    public Optional<BackupInfo> inspect(Path zip) {
        try {
            return Optional.of(backupService.inspect(zip));
        } catch (RuntimeException failure) {
            fail("settings.data.restoreFailed", failure);
            return Optional.empty();
        }
    }

    /** Replaces all current data with the backup; call only after the learner confirmed. */
    public void restore(Path zip) {
        run(() -> {
            backupService.restore(zip);
            return zip;
        }, restored -> {
            dataChanges.markChanged();
            refresh();
            succeed(messages.get("settings.data.restoreDone", restored.getFileName()));
        }, "settings.data.restoreFailed");
    }

    private <T> void run(java.util.concurrent.Callable<T> work, java.util.function.Consumer<T> onSuccess,
                         String failureKey) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        message.set("");
        failed.set(false);
        background.execute(() -> {
            T result = null;
            RuntimeException problem = null;
            try {
                result = work.call();
            } catch (RuntimeException failure) {
                problem = failure;
            } catch (Exception checked) {
                problem = new IllegalStateException(checked.getMessage(), checked);
            }
            T done = result;
            RuntimeException error = problem;
            ui.execute(() -> {
                busy.set(false);
                if (error == null) {
                    onSuccess.accept(done);
                } else {
                    fail(failureKey, error);
                }
            });
        });
    }

    private void succeed(String text) {
        failed.set(false);
        message.set(text);
    }

    private void fail(String key, Throwable failure) {
        failed.set(true);
        message.set(messages.get(key, messages.error(failure)));
    }

    public ObservableList<Entry> backups() {
        return backupsReadOnly;
    }

    /** The outcome of the last action, or empty. */
    public ReadOnlyStringProperty messageProperty() {
        return message.getReadOnlyProperty();
    }

    /** Whether {@link #messageProperty()} reports a failure. */
    public ReadOnlyBooleanProperty failedProperty() {
        return failed.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }
}
