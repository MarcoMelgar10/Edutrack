package com.edutrack.desktop.ui.settings;

import com.edutrack.desktop.ui.common.Language;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.theme.Theme;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.dialog.Confirmations;
import atlantafx.base.theme.Styles;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Function;

/**
 * Binds {@code SettingsView.fxml} to {@link SettingsViewModel}. Nothing is "applied": each control
 * writes its setting as soon as it changes, and a changed language redraws the window.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SettingsController {

    private final SettingsViewModel viewModel;
    private final Messages messages;
    private final BackupViewModel backups;
    private final Confirmations confirmations;

    @FXML
    private ComboBox<Language> languageBox;
    @FXML
    private ComboBox<Theme> themeBox;
    @FXML
    private ComboBox<DayOfWeek> weekStartBox;
    @FXML
    private ComboBox<DayOfWeek> checkInBox;
    @FXML
    private Spinner<Integer> newCardsSpinner;
    @FXML
    private Spinner<Integer> reviewLimitSpinner;
    @FXML
    private Spinner<Integer> focusSpinner;
    @FXML
    private Spinner<Integer> breakSpinner;
    @FXML
    private Spinner<Integer> reminderSpinner;
    @FXML
    private CheckBox notificationsCheck;
    @FXML
    private CheckBox trayCheck;
    @FXML
    private CheckBox startupCheck;
    @FXML
    private TextField backupFolderField;
    @FXML
    private Button browseButton;
    @FXML
    private CheckBox weeklyBackupCheck;
    @FXML
    private Button backupNowButton;
    @FXML
    private Button restoreButton;
    @FXML
    private Label backupMessage;
    @FXML
    private VBox backupList;

    public SettingsController(SettingsViewModel viewModel, Messages messages, BackupViewModel backups,
                              Confirmations confirmations) {
        this.viewModel = viewModel;
        this.messages = messages;
        this.backups = backups;
        this.confirmations = confirmations;
    }

    @FXML
    private void initialize() {
        UiPreferences settings = viewModel.preferences();

        choices(languageBox, List.of(Language.values()), Language::nativeName, settings.getLanguage(),
                language -> Platform.runLater(() -> viewModel.setLanguage(language))); // after the popup closed

        choices(themeBox, List.of(Theme.values()), theme -> messages.get("theme." + theme.name()),
                viewModel.themeProperty().get(), viewModel::setTheme);
        // The rail's moon/sun button changes the theme too.
        viewModel.themeProperty().subscribe(theme -> themeBox.setValue(theme));

        choices(weekStartBox, List.of(DayOfWeek.MONDAY, DayOfWeek.SUNDAY), this::dayName,
                settings.getWeekStart(), settings::setWeekStart);
        choices(checkInBox, List.of(DayOfWeek.values()), this::dayName,
                settings.getCheckInDay(), settings::setCheckInDay);

        number(newCardsSpinner, 0, UiPreferences.NEW_CARDS_MAX, settings.getDailyNewCards(), settings::setDailyNewCards);
        number(reviewLimitSpinner, 0, UiPreferences.REVIEW_LIMIT_MAX, settings.getDailyReviewLimit(),
                settings::setDailyReviewLimit);
        number(focusSpinner, UiPreferences.FOCUS_MINUTES_MIN, UiPreferences.FOCUS_MINUTES_MAX,
                settings.getPomodoroFocusMinutes(), settings::setPomodoroFocusMinutes);
        number(breakSpinner, UiPreferences.BREAK_MINUTES_MIN, UiPreferences.BREAK_MINUTES_MAX,
                settings.getPomodoroBreakMinutes(), settings::setPomodoroBreakMinutes);
        number(reminderSpinner, 0, UiPreferences.REMINDER_HOUR_MAX, settings.getReminderHour(), settings::setReminderHour);
        reminderSpinner.getValueFactory().setConverter(new StringConverter<>() {
            @Override
            public String toString(Integer hour) {
                return hour == null ? "" : String.format("%02d:00", hour);
            }

            @Override
            public Integer fromString(String text) {
                return Integer.parseInt(text.strip().replaceFirst("[:.].*$", ""));
            }
        });

        reminderSpinner.getEditor().setText(reminderSpinner.getValueFactory().getConverter()
                .toString(reminderSpinner.getValue()));

        check(notificationsCheck,settings.isNotificationsEnabled(), settings::setNotificationsEnabled);
        check(trayCheck, settings.isMinimizeToTray(), settings::setMinimizeToTray);
        check(startupCheck, settings.isStartWithWindows(), settings::setStartWithWindows);
        check(weeklyBackupCheck, settings.isAutomaticWeeklyBackup(), settings::setAutomaticWeeklyBackup);

        backupFolderField.textProperty().bind(settings.backupFolderProperty());

        backupNowButton.disableProperty().bind(backups.busyProperty());
        restoreButton.disableProperty().bind(backups.busyProperty());
        backups.messageProperty().subscribe(this::showBackupMessage);
        backups.failedProperty().subscribe(failed -> showBackupMessage(backups.messageProperty().get()));
        backups.backups().addListener((javafx.collections.ListChangeListener<BackupViewModel.Entry>) change -> drawBackups());
        settings.backupFolderProperty().subscribe(folder -> backups.refresh());
        backups.refresh();
        drawBackups();
    }

    private void showBackupMessage(String text) {
        boolean shown = text != null && !text.isEmpty();
        backupMessage.setText(shown ? text : "");
        backupMessage.setVisible(shown);
        backupMessage.setManaged(shown);
        backupMessage.getStyleClass().remove(Styles.DANGER);
        if (backups.failedProperty().get()) {
            backupMessage.getStyleClass().add(Styles.DANGER);
        }
    }

    private void drawBackups() {
        backupList.getChildren().clear();
        if (backups.backups().isEmpty()) {
            Label none = new Label(messages.get("settings.data.noBackups"));
            none.getStyleClass().add("text-caption");
            backupList.getChildren().add(none);
            return;
        }
        for (BackupViewModel.Entry entry : backups.backups()) {
            Label when = new Label(entry.madeAt());
            when.setMinWidth(150);
            Label size = new Label(entry.size());
            size.getStyleClass().add("text-caption");
            backupList.getChildren().add(new javafx.scene.layout.HBox(12, when, size));
        }
    }

    @FXML
    private void backUpNow() {
        backups.backUpNow();
    }

    @FXML
    private void restore() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("settings.data.chooseBackup"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("settings.data.backupFiles"), "*.zip"));
        Path start = viewModel.chooserStart();
        if (start != null) {
            chooser.setInitialDirectory(start.toFile());
        }
        File chosen = chooser.showOpenDialog(restoreButton.getScene().getWindow());
        if (chosen == null) {
            return;
        }
        Path zip = chosen.toPath();
        backups.inspect(zip).ifPresent(info -> {
            String madeOn = Formats.date(info.createdAt(), java.time.ZoneId.systemDefault()) + " " + Formats.time(info.createdAt(), java.time.ZoneId.systemDefault());
            if (confirmations.confirmRestore(restoreButton.getScene().getWindow(), madeOn, info.rows(), info.files())) {
                backups.restore(zip);
            }
        });
    }

    @FXML
    private void browse() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.get("settings.data.chooseFolder"));
        Path start = viewModel.chooserStart();
        if (start != null) {
            chooser.setInitialDirectory(start.toFile());
        }
        File chosen = chooser.showDialog(browseButton.getScene().getWindow());
        if (chosen != null) {
            viewModel.setBackupFolder(chosen.toPath());
        }
    }

    private String dayName(DayOfWeek day) {
        String name = day.getDisplayName(TextStyle.FULL, messages.locale());
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static <T> void choices(ComboBox<T> box, List<T> values, Function<T, String> label, T current,
                                    java.util.function.Consumer<T> save) {
        box.setItems(FXCollections.observableArrayList(values));
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String text) {
                return null; // the combo box is not editable
            }
        });
        box.setValue(current);
        box.valueProperty().addListener((observable, before, after) -> {
            if (after != null) {
                save.accept(after);
            }
        });
    }

    /** An editable spinner that saves each valid value at once and takes typed text when it loses focus. */
    private static void number(Spinner<Integer> spinner, int min, int max, int current, IntConsumer save) {
        SpinnerValueFactory.IntegerSpinnerValueFactory factory =
                new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, current);
        spinner.setValueFactory(factory);
        spinner.setEditable(true);
        factory.valueProperty().addListener((observable, before, after) -> {
            if (after != null) {
                save.accept(after);
            }
        });
        spinner.focusedProperty().addListener((observable, was, focused) -> {
            if (!focused) {
                commitTypedText(spinner, factory, min, max);
            }
        });
        spinner.getEditor().setOnAction(event -> commitTypedText(spinner, factory, min, max));
    }

    private static void commitTypedText(Spinner<Integer> spinner, SpinnerValueFactory.IntegerSpinnerValueFactory factory,
                                        int min, int max) {
        try {
            int typed = factory.getConverter().fromString(spinner.getEditor().getText());
            factory.setValue(Math.max(min, Math.min(max, typed)));
        } catch (NumberFormatException notANumber) {
            // fall through: put the last valid value back
        }
        spinner.getEditor().setText(factory.getConverter().toString(factory.getValue()));
    }

    private static void check(CheckBox box, boolean current, java.util.function.Consumer<Boolean> save) {
        box.setSelected(current);
        box.selectedProperty().addListener((observable, before, after) -> save.accept(after));
    }
}
