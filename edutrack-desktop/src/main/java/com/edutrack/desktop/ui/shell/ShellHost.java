package com.edutrack.desktop.ui.shell;

import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Language;
import com.edutrack.desktop.ui.common.PendingWork;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.desktop.ui.theme.Crossfade;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import org.springframework.stereotype.Component;

/**
 * Owns the {@link StackPane} that is the scene root and the {@link ShellController} inside it, and is
 * the one who can replace that shell: a language switch rebuilds the whole window (every label comes
 * from the bundle when its view is loaded) and brings the learner back to the page that was showing.
 */
@Component
public class ShellHost {

    private final ViewLoader viewLoader;
    private final PendingWork pendingWork;
    private final UiPreferences preferences;

    private StackPane host;
    private ShellController shell;

    public ShellHost(ViewLoader viewLoader, PendingWork pendingWork, UiPreferences preferences) {
        this.viewLoader = viewLoader;
        this.pendingWork = pendingWork;
        this.preferences = preferences;
    }

    /** Builds the shell inside a new scene root; ThemeManager needs the root to be a StackPane. */
    public StackPane open() {
        ViewLoader.LoadedView<ShellController> view = viewLoader.load(ShellController.class);
        shell = view.controller();
        host = new StackPane(view.root());
        return host;
    }

    /**
     * Saves the language, saves unsaved typing, rebuilds the shell in the new language and reopens the
     * page that was showing; a snapshot crossfade hides the swap, as for the theme.
     */
    public void changeLanguage(Language next) {
        preferences.setLanguage(next);
        if (host == null || next == I18n.language()) {
            I18n.activate(next);
            return;
        }
        pendingWork.flushAll();
        ShellController.Location where = shell.location();
        Runnable rebuild = () -> {
            I18n.activate(next);
            ShellController old = shell;
            ViewLoader.LoadedView<ShellController> view = viewLoader.load(ShellController.class);
            shell = view.controller();
            // Replace by position: the crossfade overlay sits at the end of the host's children.
            host.getChildren().set(0, view.root());
            shell.restore(where);
            old.dispose();
        };
        Scene scene = host.getScene();
        if (scene == null) {
            rebuild.run();
        } else {
            Crossfade.run(scene, rebuild);
        }
    }

    /** The scene root: pages and overlays (the quick search) are added to it. */
    public StackPane root() {
        return host;
    }

    /** The current shell, e.g. to dock something into it. */
    public ShellController shell() {
        return shell;
    }
}
