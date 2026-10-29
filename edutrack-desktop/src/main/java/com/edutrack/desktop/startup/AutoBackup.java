package com.edutrack.desktop.startup;

import com.edutrack.application.backup.BackupService;
import com.edutrack.desktop.ui.settings.UiPreferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Executor;

/**
 * The automatic weekly backup, run once when the app starts: when the setting is on and the newest
 * backup in the backup folder is a week old (or there is none), a new one is made off the FX thread.
 * The age is judged by {@link BackupService}, which owns the clock. A failure is logged and never
 * stops the app; the learner can always back up by hand from Settings.
 */
@Component
public class AutoBackup {

    private static final Logger LOGGER = LoggerFactory.getLogger(AutoBackup.class);

    private final BackupService backupService;
    private final UiPreferences preferences;
    private final Executor background;

    @Autowired
    public AutoBackup(BackupService backupService, UiPreferences preferences) {
        this(backupService, preferences, command -> {
            Thread worker = new Thread(command, "auto-backup");
            worker.setDaemon(true);
            worker.start();
        });
    }

    /** For tests: who runs the work. */
    public AutoBackup(BackupService backupService, UiPreferences preferences, Executor background) {
        this.backupService = backupService;
        this.preferences = preferences;
        this.background = background;
    }

    /** Starts the check in the background and returns at once. */
    public void runInBackground() {
        background.execute(this::runNow);
    }

    /** The check itself; returns the new backup, or empty when none was due or it failed. */
    Optional<Path> runNow() {
        try {
            Optional<Path> made = backupService.autoBackupIfDue(Path.of(preferences.getBackupFolder()),
                    preferences.isAutomaticWeeklyBackup());
            made.ifPresent(file -> LOGGER.info("Automatic backup written to {}", file));
            return made;
        } catch (RuntimeException failure) {
            LOGGER.warn("Automatic backup failed", failure);
            return Optional.empty();
        }
    }
}
