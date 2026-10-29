package com.edutrack.desktop.system;

import com.edutrack.desktop.ui.settings.UiPreferences;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Ties the window to Windows: tray icon, minimize-to-tray on close, reminder toasts and the
 * start-with-Windows registry value. Each follows its setting at once, no restart needed.
 */
@Component
public class DesktopIntegration {

    private static final Logger LOGGER = LoggerFactory.getLogger(DesktopIntegration.class);

    private final UiPreferences preferences;
    private final TrayService tray;
    private final WindowsStartup startup;
    private final ReminderNotifier notifier;

    public DesktopIntegration(UiPreferences preferences, TrayService tray, SpringReminderSource source, Clock clock) {
        this.preferences = preferences;
        this.tray = tray;
        this.startup = new RegistryWindowsStartup();
        this.notifier = new ReminderNotifier(source,
                reminder -> tray.toast(reminder.title(), reminder.body()), clock, preferences::isNotificationsEnabled);
    }

    public void install(Stage stage) {
        boolean hasTray = tray.install(
                () -> Platform.runLater(() -> show(stage)),
                () -> Platform.runLater(Platform::exit));

        applyImplicitExit();
        preferences.minimizeToTrayProperty().subscribe(enabled -> applyImplicitExit());
        stage.setOnCloseRequest(event -> {
            if (hasTray && preferences.isMinimizeToTray()) {
                event.consume();
                stage.hide();
            }
        });

        syncStartup(preferences.isStartWithWindows());
        preferences.startWithWindowsProperty().subscribe(this::syncStartup);

        if (hasTray) {
            notifier.start();
        }
    }

    public void dispose() {
        notifier.stop();
        tray.remove();
    }

    private void applyImplicitExit() {
        // Stay alive with no window only while the tray can bring it back.
        Platform.setImplicitExit(!(tray.isInstalled() && preferences.isMinimizeToTray()));
    }

    private static void show(Stage stage) {
        stage.show();
        stage.setIconified(false);
        stage.toFront();
    }

    private void syncStartup(boolean enabled) {
        Thread worker = new Thread(() -> {
            try {
                startup.setEnabled(enabled);
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not update start with Windows", failure);
            }
        }, "startup-registry");
        worker.setDaemon(true);
        worker.start();
    }
}
