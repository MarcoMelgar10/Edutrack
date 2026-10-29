package com.edutrack.desktop.ui.common;

import com.edutrack.application.NotFoundException;
import com.edutrack.desktop.ui.theme.ThemeManager;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Last line of defence for unexpected errors on the JavaFX thread: logs them and shows a calm,
 * formal message instead of failing silently. Expected validation errors never get here —
 * dialogs show those inline.
 */
@Component
public class ErrorReporter {

    private static final Logger log = LoggerFactory.getLogger(ErrorReporter.class);

    private final ThemeManager themeManager;
    private final DataChanges dataChanges;
    private final Messages messages;

    public ErrorReporter(ThemeManager themeManager, DataChanges dataChanges, Messages messages) {
        this.themeManager = themeManager;
        this.dataChanges = dataChanges;
        this.messages = messages;
    }

    /** Routes every uncaught exception of the calling (FX) thread to {@link #report(Throwable)}. */
    public void installOnCurrentThread() {
        Thread.currentThread().setUncaughtExceptionHandler((thread, error) -> report(error));
    }

    public void report(Throwable error) {
        Throwable cause = rootOf(error);
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("EduTrack");
        if (cause instanceof NotFoundException) {
            log.info("Stale view: {}", cause.getMessage());
            alert.setHeaderText(messages.get("error.gone.header"));
            alert.setContentText(messages.get("error.gone.content"));
            dataChanges.markChanged();
        } else {
            log.error("Unexpected error", error);
            alert.setHeaderText(messages.get("error.unexpected.header"));
            alert.setContentText(messages.get("error.unexpected.content"));
            alert.getDialogPane().setExpandableContent(details(error));
        }
        themeManager.style(alert.getDialogPane());
        alert.showAndWait();
    }

    private static Throwable rootOf(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            if (current instanceof NotFoundException) {
                return current;
            }
            current = current.getCause();
        }
        return current;
    }

    private static TextArea details(Throwable error) {
        StringWriter trace = new StringWriter();
        error.printStackTrace(new PrintWriter(trace));
        TextArea area = new TextArea(trace.toString());
        area.setEditable(false);
        area.setPrefRowCount(12);
        area.getStyleClass().add("monospace");
        return area;
    }
}
