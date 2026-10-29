package com.edutrack.desktop.ui.theme;

import javafx.application.Application;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ObservableList;
import javafx.scene.control.DialogPane;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Applies the current {@link Theme} and switches between light and dark without a visible jump.
 *
 * <p>The switch is a {@link Crossfade}: because only the token stylesheet differs between
 * themes, the user sees the neutrals shift while every shape, icon and label stays in place.
 * The native Windows title bar follows along, so no white strip remains in dark mode.
 */
@Component
public class ThemeManager {

    private final ReadOnlyObjectWrapper<Theme> theme = new ReadOnlyObjectWrapper<>(ThemePreferences.load());
    private Stage stage;

    /**
     * Attaches the theme to the main window. Call after {@code stage.setScene(...)} and before
     * {@code show()}; the scene root must be a {@link StackPane} (the crossfade host).
     */
    public void install(Stage stage) {
        this.stage = stage;
        applyTo(theme.get(), stage.getScene().getStylesheets());
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, shown -> updateTitleBar(stage, theme.get()));
    }

    /** Styles a dialog with the current theme; dialogs are modal, so they never see a live switch. */
    public void style(DialogPane dialogPane) {
        styleDialog(dialogPane, theme.get());
    }

    /** Same as {@link #style(DialogPane)}, usable before Spring is up (start-up error dialog). */
    public static void styleDialog(DialogPane dialogPane, Theme theme) {
        applyTo(theme, dialogPane.getStylesheets());
        if (dialogPane.getScene() != null && dialogPane.getScene().getWindow() instanceof Stage dialogStage) {
            dialogStage.addEventHandler(WindowEvent.WINDOW_SHOWN, shown -> updateTitleBar(dialogStage, theme));
        }
    }

    public ReadOnlyObjectProperty<Theme> themeProperty() {
        return theme.getReadOnlyProperty();
    }

    public void toggle() {
        setTheme(theme.get().opposite());
    }

    public void setTheme(Theme next) {
        if (next == theme.get()) {
            return;
        }
        theme.set(next);
        ThemePreferences.save(next);
        if (stage != null) {
            Crossfade.run(stage.getScene(), () -> applyTo(next, stage.getScene().getStylesheets()));
            updateTitleBar(stage, next);
        }
    }

    /** Sets the global AtlantaFX base and puts the theme's tokens ahead of the shared stylesheets. */
    private static void applyTo(Theme theme, ObservableList<String> stylesheets) {
        Application.setUserAgentStylesheet(theme.userAgentStylesheet());
        stylesheets.removeAll(Theme.LIGHT.tokensCss(), Theme.DARK.tokensCss(), Theme.BASE_CSS, Theme.FEATURES_CSS);
        stylesheets.addAll(0, List.of(theme.tokensCss(), Theme.BASE_CSS, Theme.FEATURES_CSS));
    }

    private static void updateTitleBar(Stage window, Theme theme) {
        WindowsTitleBar.apply(window, window.getTitle(), theme == Theme.DARK);
    }
}
