package com.edutrack.desktop.ui.journal;

import atlantafx.base.theme.Styles;
import com.edutrack.application.journal.JournalEntryView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.LabeledChoice;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.NoteEditor;
import com.edutrack.desktop.ui.common.NoteEditors;
import com.edutrack.desktop.ui.common.StudyTime;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.DeadlineConverter;
import com.edutrack.desktop.ui.markdown.MarkdownView;
import com.edutrack.desktop.ui.shell.PageLifecycle;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.BooleanSupplier;

/**
 * Binds {@code JournalView.fxml} to {@link JournalViewModel}: the filtered entry list on the left and, on the
 * right, either the selected entry rendered as Markdown or the editor for a note or the weekly check-in.
 * The body of the editor comes from {@link NoteEditors#create()} — the one place to change the editor.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class JournalController implements PageLifecycle {

    private final JournalViewModel viewModel;
    private final Confirmations confirmations;
    private final HostServices hostServices;
    private final Messages messages;

    @FXML
    private VBox listPane;
    @FXML
    private Button newNoteButton;
    @FXML
    private Button checkInButton;
    @FXML
    private VBox filters;
    @FXML
    private ComboBox<LabeledChoice<AreaId>> areaBox;
    @FXML
    private ComboBox<LabeledChoice<GoalId>> goalBox;
    @FXML
    private ToggleButton kindAll;
    @FXML
    private ToggleButton kindNotes;
    @FXML
    private ToggleButton kindCheckIns;
    @FXML
    private ListView<JournalEntryView> entryList;

    @FXML
    private VBox emptyPane;
    @FXML
    private VBox readPane;
    @FXML
    private HBox readHeader;
    @FXML
    private Label readTitle;
    @FXML
    private Label readMeta;
    @FXML
    private HBox checkInMeta;
    @FXML
    private Label confidenceLabel;
    @FXML
    private Label readWeek;
    @FXML
    private MarkdownView readBody;

    @FXML
    private VBox editPane;
    @FXML
    private Label editorTitleLabel;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField titleField;
    @FXML
    private ComboBox<LabeledChoice<AreaId>> editAreaBox;
    @FXML
    private ComboBox<LabeledChoice<GoalId>> editGoalBox;
    @FXML
    private ComboBox<LabeledChoice<StepId>> editStepBox;
    @FXML
    private VBox checkInFields;
    @FXML
    private HBox confidenceButtons;
    @FXML
    private Label weekLabel;
    @FXML
    private StackPane editorHost;
    @FXML
    private Label errorLabel;
    @FXML
    private Button saveButton;

    private final FontIcon readIcon = new FontIcon(Feather.EDIT_3);
    private final ConfidenceDots readDots = new ConfidenceDots();
    private final ToggleGroup confidenceGroup = new ToggleGroup();
    private final ToggleGroup kindGroup = new ToggleGroup();
    private DeadlineConverter dateConverter;
    private NoteEditor noteEditor;
    private boolean clearingConfidence;

    public JournalController(JournalViewModel viewModel, Confirmations confirmations, HostServices hostServices,
                             Messages messages) {
        this.viewModel = viewModel;
        this.confirmations = confirmations;
        this.hostServices = hostServices;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        initializeFilters();
        initializeList();
        initializeRead();
        initializeEditor();
        initializePanes();
    }

    @Override
    public void shown() {
        viewModel.refresh();
    }

    @Override
    public void disposed() {
        viewModel.dispose();
    }

    // ---- entry points used from elsewhere in the app (via Navigator) ----

    /** Opens the editor for the weekly check-in. */
    public void startCheckIn() {
        viewModel.startCheckIn();
    }

    /** Opens the editor for a new note, linked to {@code goalId} when it is not {@code null}. */
    public void startNote(GoalId goalId) {
        viewModel.startNote(goalId);
    }

    // ---- left: filters and list ----

    private void initializeFilters() {
        LabeledChoice.bind(areaBox, viewModel.areaFilterProperty(), viewModel.areas(),
                () -> LabeledChoice.listOf(messages.get("journal.filter.allAreas"), viewModel.areas(),
                        area -> area.id(), area -> area.name()));
        LabeledChoice.bind(goalBox, viewModel.goalFilterProperty(), viewModel.filterGoals(),
                () -> LabeledChoice.listOf(messages.get("journal.filter.allGoals"), viewModel.filterGoals(),
                        goal -> goal.id(), goal -> goal.title()));
        areaBox.setAccessibleText(messages.get("journal.link.area"));
        goalBox.setAccessibleText(messages.get("journal.link.goal"));

        // The kind filter is one segmented control; one segment is always on.
        segment(kindAll, JournalViewModel.KindFilter.ALL, Styles.LEFT_PILL);
        segment(kindNotes, JournalViewModel.KindFilter.NOTES, Styles.CENTER_PILL);
        segment(kindCheckIns, JournalViewModel.KindFilter.CHECK_INS, Styles.RIGHT_PILL);
        keepOneSelected(kindGroup, () -> false);
        kindGroup.selectedToggleProperty().subscribe(toggle -> {
            if (toggle != null) {
                viewModel.kindFilterProperty().set((JournalViewModel.KindFilter) toggle.getUserData());
            }
        });
        viewModel.kindFilterProperty().subscribe(kind -> kindGroup.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == kind).findFirst().ifPresent(kindGroup::selectToggle));

        // While an entry is being edited the list and the filters stay as they are.
        filters.disableProperty().bind(viewModel.editingProperty());
        entryList.disableProperty().bind(viewModel.editingProperty());
        newNoteButton.disableProperty().bind(viewModel.editingProperty());
        checkInButton.disableProperty().bind(viewModel.editingProperty());
    }

    private void segment(ToggleButton button, JournalViewModel.KindFilter kind, String position) {
        button.setToggleGroup(kindGroup);
        button.setUserData(kind);
        button.getStyleClass().add(position);
    }

    /** A toggle group lets the selected button be clicked off; this puts it back on (unless {@code clearing}). */
    private static void keepOneSelected(ToggleGroup group, BooleanSupplier clearing) {
        group.selectedToggleProperty().subscribe((before, after) -> {
            if (after == null && before != null && !clearing.getAsBoolean()) {
                before.setSelected(true);
            }
        });
    }

    private void initializeList() {
        entryList.setItems(viewModel.entries());
        entryList.setCellFactory(JournalEntryCell.factory(viewModel));
        // The view model owns the selection; the list follows it and tells it what the user picked.
        entryList.getSelectionModel().selectedItemProperty().subscribe(entry -> {
            if (entry != null && !entry.equals(viewModel.selectedProperty().get())) {
                viewModel.selectedProperty().set(entry);
            }
        });
        viewModel.selectedProperty().subscribe(entry -> {
            if (entry == null) {
                entryList.getSelectionModel().clearSelection();
            } else if (!entry.equals(entryList.getSelectionModel().getSelectedItem())) {
                entryList.getSelectionModel().select(entry);
            }
        });
        viewModel.entries().addListener((ListChangeListener<JournalEntryView>) change -> {
            // setAll clears the list view's selection; put it back on the entry the view model shows
            JournalEntryView current = viewModel.selectedProperty().get();
            if (current != null && !current.equals(entryList.getSelectionModel().getSelectedItem())) {
                entryList.getSelectionModel().select(current);
            }
        });
    }

    // ---- right: reading ----

    private void initializeRead() {
        readHeader.getChildren().addFirst(readIcon);
        readIcon.getStyleClass().add("journal-kind-icon");
        checkInMeta.getChildren().addFirst(readDots);
        readBody.setOnLink(this::openLink);
        viewModel.selectedProperty().subscribe(this::renderRead);
    }

    private void renderRead(JournalEntryView entry) {
        if (entry == null) {
            return;
        }
        boolean checkIn = entry.kind() == EntryKind.CHECK_IN;
        readIcon.setIconCode(checkIn ? Feather.CHECK_SQUARE : Feather.EDIT_3);
        readTitle.setText(viewModel.title(entry));
        String caption = viewModel.caption(entry);
        readMeta.setText(caption.isEmpty() ? Formats.longDate(entry.writtenOn())
                : Formats.longDate(entry.writtenOn()) + " · " + caption);
        checkInMeta.setVisible(checkIn);
        checkInMeta.setManaged(checkIn);
        if (checkIn) {
            readDots.show(entry.confidence());
            confidenceLabel.setText(messages.get("journal.confidence.level." + entry.confidence()));
            readWeek.setText(entry.weekMinutes() == null ? ""
                    : messages.get("journal.weekStudied", StudyTime.hoursText(entry.weekMinutes())));
        }
        readBody.setMarkdown(entry.body());
    }

    @FXML
    private void edit() {
        viewModel.edit();
    }

    @FXML
    private void delete() {
        JournalEntryView entry = viewModel.selectedProperty().get();
        if (entry != null && confirmations.confirmDelete(window(), "entry", viewModel.title(entry), null)) {
            viewModel.deleteSelected();
        }
    }

    /** Only web and mail links leave the app. */
    private void openLink(String destination) {
        if (destination != null && destination.matches("(?i)(https?://|mailto:).+")) {
            hostServices.showDocument(destination);
        }
    }

    // ---- right: editing ----

    private void initializeEditor() {
        editorTitleLabel.textProperty().bind(viewModel.editorTitleProperty());

        dateConverter = new DeadlineConverter();
        datePicker.setConverter(dateConverter);
        datePicker.valueProperty().bindBidirectional(viewModel.editDateProperty());
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate date, boolean empty) {
                super.updateItem(date, empty);
                setDisable(!empty && date.isAfter(viewModel.today()));
            }
        });
        titleField.textProperty().bindBidirectional(viewModel.editTitleProperty());

        LabeledChoice.bind(editAreaBox, viewModel.editAreaProperty(), viewModel.areas(),
                () -> LabeledChoice.listOf(messages.get("journal.link.none"), viewModel.areas(),
                        area -> area.id(), area -> area.name()));
        LabeledChoice.bind(editGoalBox, viewModel.editGoalProperty(), viewModel.editGoals(),
                () -> LabeledChoice.listOf(messages.get("journal.link.none"), viewModel.editGoals(),
                        goal -> goal.id(), goal -> goal.title()));
        LabeledChoice.bind(editStepBox, viewModel.editStepProperty(), viewModel.editSteps(),
                () -> LabeledChoice.listOf(messages.get("journal.link.none"), viewModel.editSteps(),
                        step -> step.id(), step -> step.number() + ". " + step.title()));

        buildConfidenceButtons();
        checkInFields.visibleProperty().bind(viewModel.editingKindProperty().isEqualTo(EntryKind.CHECK_IN));
        checkInFields.managedProperty().bind(checkInFields.visibleProperty());
        weekLabel.textProperty().bind(viewModel.weekTextProperty());

        noteEditor = NoteEditors.create();
        noteEditor.setPromptText(messages.get("journal.bodyPrompt"));
        editorHost.getChildren().add(noteEditor.node());
        noteEditor.textProperty().bindBidirectional(viewModel.editBodyProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        saveButton.disableProperty().bind(viewModel.validProperty().not());

        // Esc leaves the editor, Ctrl+Enter saves; both are caught before a text control sees them.
        KeyCombination saveKeys = new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN);
        editPane.addEventFilter(KeyEvent.KEY_PRESSED, key -> {
            if (saveKeys.match(key)) {
                if (!saveButton.isDisabled()) {
                    save();
                }
                key.consume();
            }
        });
        editPane.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ESCAPE) {
                cancel();
                key.consume();
            }
        });
    }

    /** Five numbered segments with "Lost" and "Confident" at the ends; the level word is the tooltip. */
    private void buildConfidenceButtons() {
        for (int level = 1; level <= ConfidenceDots.DOTS; level++) {
            ToggleButton button = new ToggleButton(String.valueOf(level));
            button.setToggleGroup(confidenceGroup);
            button.setUserData(level);
            button.getStyleClass().add(level == 1 ? Styles.LEFT_PILL
                    : level == ConfidenceDots.DOTS ? Styles.RIGHT_PILL : Styles.CENTER_PILL);
            String word = messages.get("journal.confidence.level." + level);
            button.setTooltip(new Tooltip(word));
            button.setAccessibleText(messages.get("journal.confidence.option", level, ConfidenceDots.DOTS, word));
            confidenceButtons.getChildren().add(button);
        }
        keepOneSelected(confidenceGroup, () -> clearingConfidence);
        confidenceGroup.selectedToggleProperty().subscribe(toggle -> {
            if (toggle != null) {
                viewModel.editConfidenceProperty().set((Integer) toggle.getUserData());
            }
        });
        viewModel.editConfidenceProperty().subscribe(level -> {
            Toggle match = confidenceGroup.getToggles().stream()
                    .filter(toggle -> toggle.getUserData().equals(level)).findFirst().orElse(null);
            if (match != null) {
                confidenceGroup.selectToggle(match);
            } else if (confidenceGroup.getSelectedToggle() != null) {
                // a new check-in starts without a level
                clearingConfidence = true;
                try {
                    confidenceGroup.selectToggle(null);
                } finally {
                    clearingConfidence = false;
                }
            }
        });
    }

    @FXML
    private void newNote() {
        viewModel.startNote(null);
    }

    @FXML
    private void newCheckIn() {
        viewModel.startCheckIn();
    }

    @FXML
    private void save() {
        // A typed date only reaches the picker's value on Enter or focus loss, so read the text itself.
        try {
            viewModel.editDateProperty().set(dateConverter.fromString(datePicker.getEditor().getText()));
        } catch (DateTimeParseException notADate) {
            String example = Formats.date(LocalDate.of(2026, 10, 12));
            viewModel.fail(new DomainValidationException("invalid-date",
                    "Date must be a date such as " + example + ".", "Date", example));
            return;
        }
        if (viewModel.save()) {
            Motion.fadeIn(readPane);
        }
    }

    @FXML
    private void cancel() {
        viewModel.cancel();
    }

    // ---- which pane shows ----

    private void initializePanes() {
        show(emptyPane, viewModel.editingProperty().not().and(viewModel.selectedProperty().isNull()));
        show(readPane, viewModel.editingProperty().not().and(viewModel.selectedProperty().isNotNull()));
        show(editPane, viewModel.editingProperty());
        viewModel.editingProperty().subscribe(editing -> {
            if (editing) {
                Motion.fadeIn(editPane);
                Platform.runLater(noteEditor::focusEditor);
            }
        });
    }

    private static void show(javafx.scene.Node pane, javafx.beans.value.ObservableBooleanValue condition) {
        pane.visibleProperty().bind(condition);
        pane.managedProperty().bind(pane.visibleProperty());
    }

    private Window window() {
        return listPane.getScene().getWindow();
    }
}
