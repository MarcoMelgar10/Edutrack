package com.edutrack.desktop.startup;

import com.edutrack.desktop.ui.common.AppIcons;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.desktop.ui.theme.ThemeManager;
import com.edutrack.desktop.ui.theme.ThemePreferences;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Shown instead of the main window when Spring could not start (usually: database not reachable).
 * Spring beans are unavailable at this point, so the theme and the language are read straight from
 * the saved preferences.
 */
public final class StartupErrorDialog {

    private StartupErrorDialog() {
    }

    public static void show(Throwable failure) {
        I18n.activate(UiPreferences.savedLanguage());
        StartupProblem problem = StartupProblem.from(failure);

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("EduTrack");
        alert.setHeaderText(problem.headline());
        alert.setContentText(problem.advice());
        ThemeManager.styleDialog(alert.getDialogPane(), ThemePreferences.load());
        alert.getDialogPane().setExpandableContent(details(failure));
        ((Stage) alert.getDialogPane().getScene().getWindow()).getIcons().setAll(AppIcons.all());
        alert.showAndWait();
    }

    private static TextArea details(Throwable failure) {
        StringWriter trace = new StringWriter();
        failure.printStackTrace(new PrintWriter(trace));
        TextArea area = new TextArea(trace.toString());
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(14);
        area.getStyleClass().add("monospace");
        return area;
    }
}
