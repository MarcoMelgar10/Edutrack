package com.edutrack.desktop.ui.settings;

import com.edutrack.desktop.ui.common.Language;
import com.edutrack.desktop.ui.shell.ShellHost;
import com.edutrack.desktop.ui.theme.Theme;
import com.edutrack.desktop.ui.theme.ThemeManager;
import javafx.beans.property.ReadOnlyObjectProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * State behind the Settings page. Every control saves the moment it changes, straight into
 * {@link UiPreferences}; the two settings that change the whole window — language and theme — go
 * through the objects that know how to redraw it.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SettingsViewModel {

    private final UiPreferences preferences;
    private final ThemeManager themeManager;
    private final ShellHost shellHost;

    public SettingsViewModel(UiPreferences preferences, ThemeManager themeManager, ShellHost shellHost) {
        this.preferences = preferences;
        this.themeManager = themeManager;
        this.shellHost = shellHost;
    }

    /** The stored settings; the page reads and writes them directly. */
    public UiPreferences preferences() {
        return preferences;
    }

    public ReadOnlyObjectProperty<Theme> themeProperty() {
        return themeManager.themeProperty();
    }

    public void setTheme(Theme theme) {
        themeManager.setTheme(theme);
    }

    /** Saves the language and rebuilds the window in it, coming back to the page that was open. */
    public void setLanguage(Language language) {
        shellHost.changeLanguage(language);
    }

    /** The folder the file chooser should open in: the backup folder if it exists, else none. */
    public Path chooserStart() {
        Path folder = Path.of(preferences.getBackupFolder());
        return Files.isDirectory(folder) ? folder : null;
    }

    public void setBackupFolder(Path folder) {
        preferences.setBackupFolder(folder.toString());
    }
}
