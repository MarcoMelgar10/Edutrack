package com.edutrack.desktop;

import com.edutrack.EduTrackApplication;
import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.desktop.startup.AutoBackup;
import com.edutrack.desktop.startup.StartupErrorDialog;
import com.edutrack.desktop.system.DesktopIntegration;
import com.edutrack.desktop.ui.common.AppIcons;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.ErrorReporter;
import com.edutrack.desktop.ui.common.PendingWork;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.desktop.ui.shell.ShellHost;
import com.edutrack.desktop.ui.theme.ThemeManager;
import com.edutrack.desktop.ui.theme.Typography;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * JavaFX lifecycle wired to the Spring context.
 *
 * <ul>
 *   <li>{@link #init()} runs on the JavaFX launcher thread (not the FX thread), so the slow parts —
 *       Spring start-up, Flyway migrations, the first DB connection — happen before the window shows.</li>
 *   <li>{@link #start(Stage)} builds the window from Spring-managed controllers.</li>
 *   <li>{@link #stop()} saves pending work (notes typed just before closing), then closes the
 *       context so the connection pool shuts down cleanly.</li>
 * </ul>
 */
public class JavaFxApplication extends Application {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaFxApplication.class);
    private static final double INITIAL_WIDTH = 1200;
    private static final double INITIAL_HEIGHT = 760;

    private ConfigurableApplicationContext context;
    private Throwable startupFailure;

    @Override
    public void init() {
        I18n.activate(UiPreferences.savedLanguage()); // before anything can show text, even a start-up error
        Typography.loadBundledFonts();
        try {
            context = new SpringApplicationBuilder(EduTrackApplication.class)
                    .headless(false)
                    .run(getParameters().getRaw().toArray(String[]::new));
        } catch (Throwable failure) {
            startupFailure = failure;
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupFailure != null) {
            StartupErrorDialog.show(startupFailure);
            Platform.exit();
            return;
        }

        removeOrphanFilesInBackground();
        context.getBean(AutoBackup.class).runInBackground();

        // Lets views open resource links in the default browser.
        context.getBeanFactory().registerSingleton("hostServices", getHostServices());
        context.getBean(ErrorReporter.class).installOnCurrentThread();

        StackPane host = context.getBean(ShellHost.class).open();
        stage.setTitle("EduTrack");
        stage.getIcons().setAll(AppIcons.all());
        stage.setMinWidth(960);
        stage.setMinHeight(600);
        stage.setScene(new Scene(host, INITIAL_WIDTH, INITIAL_HEIGHT));
        context.getBean(ThemeManager.class).install(stage);
        stage.show();
        context.getBean(DesktopIntegration.class).install(stage);
    }

    /**
     * Attached files of goals and steps deleted in earlier sessions are still on disk (the database
     * only cascades rows). They are cleaned once per start, off the FX thread, and never block start-up.
     */
    private void removeOrphanFilesInBackground() {
        AttachmentService attachments = context.getBean(AttachmentService.class);
        Thread cleanup = new Thread(() -> {
            try {
                int removed = attachments.removeOrphans();
                if (removed > 0) {
                    LOGGER.info("Removed {} orphaned attachment file(s)", removed);
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Could not clean up orphaned attachment files", failure);
            }
        }, "attachment-cleanup");
        cleanup.setDaemon(true);
        cleanup.start();
    }

    @Override
    public void stop() {
        if (context != null) {
            context.getBean(DesktopIntegration.class).dispose();
            context.getBean(PendingWork.class).flushAll();
            context.close();
        }
    }
}
