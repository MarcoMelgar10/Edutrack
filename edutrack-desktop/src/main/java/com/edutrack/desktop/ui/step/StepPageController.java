package com.edutrack.desktop.ui.step;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.record.RecordView.ExerciseView;
import com.edutrack.application.record.RecordView.ResourceView;
import com.edutrack.application.record.RecordView.ResultView;
import com.edutrack.desktop.ui.cards.Cards;
import com.edutrack.desktop.ui.cards.CardsPane;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.files.FilesPane;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.ExerciseDialogController;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.dialog.ResourceDialogController;
import com.edutrack.desktop.ui.dialog.ResultDialogController;
import com.edutrack.desktop.ui.notes.NoteEditor;
import com.edutrack.desktop.ui.roadmap.StepMark;
import com.edutrack.desktop.ui.session.SessionListController;
import com.edutrack.desktop.ui.shell.FocusMode;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import javafx.animation.PauseTransition;
import javafx.application.HostServices;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Binds {@code StepPageView.fxml} to {@link StepPageViewModel}: the step's header and its four tabs —
 * Markdown notes (autosaved, with a live preview), resources, exercises and results. Dialogs and
 * confirmations are opened here; every change goes through the view model.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StepPageController {

    private static final Duration AUTOSAVE_DELAY = Duration.millis(800);

    private final StepPageViewModel viewModel;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final HostServices hostServices;
    private final Messages messages;
    private final ViewLoader viewLoader;
    private final ObjectProvider<CardsPane> cardsBean;
    private final Cards cards;
    private final ObjectProvider<FilesPane> filesBean;
    private final FocusMode focusMode;
    /** Tabs added next to the built-in ones (sessions, cards, files…): what to do when the step changes. */
    private final List<Panel> panels = new ArrayList<>();

    private record Panel(BiConsumer<GoalId, StepId> show, Runnable clear) {
    }

    @FXML
    private VBox root;
    @FXML
    private Button expandButton;
    @FXML
    private Hyperlink backLink;
    @FXML
    private Label crumbStepLabel;
    @FXML
    private Button markButton;
    @FXML
    private Label titleLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private TabPane tabs;
    @FXML
    private Tab notesTab;
    @FXML
    private Tab resourcesTab;
    @FXML
    private Tab exercisesTab;
    @FXML
    private Tab resultsTab;
    @FXML
    private Label saveStateLabel;
    @FXML
    private NoteEditor notesEditor;
    @FXML
    private Button addResourceButton;
    @FXML
    private Button addResultButton;
    @FXML
    private ListView<ResourceView> resourceList;
    @FXML
    private ListView<ExerciseView> exerciseList;
    @FXML
    private ListView<ResultView> resultList;
    @FXML
    private TextField exerciseInput;
    @FXML
    private Label exerciseErrorLabel;

    private final StepMark mark = new StepMark();
    private final PauseTransition autosave = new PauseTransition(AUTOSAVE_DELAY);
    private Runnable onBack = () -> { };

    public StepPageController(StepPageViewModel viewModel, FormDialogs dialogs, Confirmations confirmations,
                              HostServices hostServices, Messages messages, ViewLoader viewLoader,
                              ObjectProvider<CardsPane> cardsBean, ObjectProvider<FilesPane> filesBean, Cards cards,
                              FocusMode focusMode) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.hostServices = hostServices;
        this.messages = messages;
        this.viewLoader = viewLoader;
        this.cardsBean = cardsBean;
        this.cards = cards;
        this.filesBean = filesBean;
        this.focusMode = focusMode;
    }

    @FXML
    private void initialize() {
        initializeHeader();
        initializeTabs();
        initializeNotes();
        initializeResources();
        initializeExercises();
        initializeResults();
        initializeSessions();
        initializeCards();
        initializeFiles();
    }

    private void initializeHeader() {
        backLink.setGraphic(new FontIcon(Feather.ARROW_LEFT));
        backLink.textProperty().bind(viewModel.goalTitleProperty());
        backLink.setTooltip(new javafx.scene.control.Tooltip(messages.get("step.backToGoal")));
        crumbStepLabel.textProperty().bind(viewModel.stepTitleProperty());

        FontIcon expandIcon = new FontIcon();
        expandButton.setGraphic(expandIcon);
        javafx.scene.control.Tooltip expandTip = new javafx.scene.control.Tooltip();
        expandButton.setTooltip(expandTip);
        focusMode.stepExpandedProperty().subscribe(expanded -> {
            String label = messages.get(expanded ? "step.collapse" : "step.expand");
            expandIcon.setIconCode(expanded ? Feather.MINIMIZE_2 : Feather.MAXIMIZE_2);
            expandTip.setText(label);
            expandButton.setAccessibleText(label);
        });

        markButton.setGraphic(mark);
        viewModel.statusProperty().subscribe(mark::show);
        titleLabel.textProperty().bind(viewModel.stepTitleProperty());
        statusLabel.textProperty().bind(Bindings.createStringBinding(
                () -> viewModel.hoursTextProperty().get().isEmpty()
                        ? viewModel.statusTextProperty().get()
                        : viewModel.statusTextProperty().get() + " · " + viewModel.hoursTextProperty().get(),
                viewModel.statusTextProperty(), viewModel.hoursTextProperty()));
    }

    private void initializeTabs() {
        // Notes is fixed; the others carry counts.
        resourcesTab.textProperty().bind(viewModel.resourcesTabTextProperty());
        exercisesTab.textProperty().bind(viewModel.exercisesTabTextProperty());
        resultsTab.textProperty().bind(viewModel.resultsTabTextProperty());
        tabs.getSelectionModel().selectedItemProperty().subscribe(selected -> viewModel.saveNotesIfChanged());
    }

    private void initializeNotes() {
        notesEditor.setPromptText(messages.get("note.prompt"));
        notesEditor.markdownProperty().bindBidirectional(viewModel.notesProperty());
        saveStateLabel.textProperty().bind(viewModel.saveTextProperty());
        viewModel.saveStateProperty().subscribe(state -> {
            boolean failed = state == StepPageViewModel.SaveState.FAILED;
            saveStateLabel.getStyleClass().remove("failed");
            if (failed) {
                saveStateLabel.getStyleClass().add("failed");
            }
        });

        notesEditor.setOnLinkClick(this::openLink);
        notesEditor.setOnMakeCard(front -> cards.openNewCard(window(), viewModel.goalId(),
                new StepId(viewModel.owner().id()), front));
        autosave.setOnFinished(finished -> viewModel.saveNotesIfChanged());
        viewModel.notesProperty().subscribe(text -> autosave.playFromStart());
        notesEditor.editorFocusedProperty().subscribe(focused -> {
            if (!focused) {
                viewModel.saveNotesIfChanged();
            }
        });
    }

    private void initializeResources() {
        resourceList.setItems(viewModel.resources());
        resourceList.setCellFactory(ResourceCell.factory(new ResourceActions()));
        addResourceButton.setGraphic(new FontIcon(Feather.PLUS));
    }

    private void initializeExercises() {
        exerciseList.setItems(viewModel.exercises());
        exerciseList.setCellFactory(ExerciseCell.factory(new ExerciseActions()));
        exerciseErrorLabel.textProperty().bind(viewModel.exerciseErrorProperty());
        exerciseErrorLabel.visibleProperty().bind(viewModel.exerciseErrorProperty().isNotEmpty());
        exerciseErrorLabel.managedProperty().bind(exerciseErrorLabel.visibleProperty());
    }

    private void initializeResults() {
        resultList.setItems(viewModel.results());
        resultList.setCellFactory(ResultCell.factory(this::editResult, this::deleteResult));
        addResultButton.setGraphic(new FontIcon(Feather.PLUS));
    }

    private void initializeSessions() {
        ViewLoader.LoadedView<SessionListController> view = viewLoader.load(SessionListController.class);
        SessionListController sessions = view.controller();
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.textProperty().bind(sessions.tabTextProperty());
        tab.setContent(view.root());
        // Sessions come right after Notes: it is the thing you do while you study.
        tabs.getTabs().add(tabs.getTabs().indexOf(notesTab) + 1, tab);
        panels.add(new Panel(sessions::showStep, sessions::clear));
    }

    private void initializeCards() {
        CardsPane cardsPane = cardsBean.getObject();
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.textProperty().bind(cardsPane.tabTextProperty());
        tab.setContent(cardsPane.content());
        // Cards go after Sessions: practice first, then what to remember.
        tabs.getTabs().add(tabs.getTabs().indexOf(notesTab) + 2, tab);
        panels.add(new Panel(cardsPane::showStep, cardsPane::clear));
    }

    private void initializeFiles() {
        FilesPane files = filesBean.getObject();
        Tab tab = new Tab();
        tab.setClosable(false);
        tab.textProperty().bind(files.tabTextProperty());
        tab.setContent(files.content());
        // Files come last: after the built-in tabs (results is the last of them).
        tabs.getTabs().add(tab);
        panels.add(new Panel(files::show, files::clear));
    }

    // ---- navigation (driven by the goal panel) ----

    /** Shows one step of {@code goal}, starting on the Notes tab. */
    public void open(GoalDetails goal, StepId stepId) {
        autosave.stop();
        viewModel.open(goal, stepId);
        panels.forEach(panel -> panel.show().accept(goal.id(), stepId));
        tabs.getSelectionModel().select(notesTab);
        exerciseInput.clear();
    }

    /** Saves pending notes and hides the page; {@code false} (page still open) when the notes could not be saved. */
    public boolean close() {
        autosave.stop();
        panels.forEach(panel -> panel.clear().run());
        return viewModel.close();
    }

    /** What to do after the user chose "back to the goal" (the page has been closed by then). */
    public void setOnBack(Runnable onBack) {
        this.onBack = onBack == null ? () -> { } : onBack;
    }

    public ReadOnlyBooleanProperty openProperty() {
        return viewModel.openProperty();
    }

    public VBox root() {
        return root;
    }

    @FXML
    private void back() {
        if (close()) {
            onBack.run();
        }
    }

    // ---- header ----

    @FXML
    private void toggleExpanded() {
        focusMode.toggleStepExpanded();
    }

    @FXML
    private void cycleStatus() {
        if (viewModel.nextClickReopens() && !confirmations.confirmReopen(statusLabel.getScene().getWindow(),
                viewModel.stepTitleProperty().get(), viewModel.completedOn())) {
            return;
        }
        viewModel.cycleStatus();
    }

    // ---- notes ----

    /** Only web and mail links leave the app. */
    private void openLink(String destination) {
        if (destination != null && destination.matches("(?i)(https?://|mailto:).+")) {
            hostServices.showDocument(destination);
        }
    }

    // ---- resources ----

    @FXML
    private void addResource() {
        dialogs.show(window(), ResourceDialogController.class, form -> form.create(viewModel.owner()))
                .ifPresent(viewModel::apply);
    }

    private final class ResourceActions implements ResourceCell.Actions {

        @Override
        public void markReviewed(ResourceView resource, boolean reviewed) {
            viewModel.markResourceReviewed(resource.id(), reviewed);
        }

        @Override
        public void openLink(String url) {
            StepPageController.this.openLink(url);
        }

        @Override
        public void edit(ResourceView resource) {
            dialogs.show(window(), ResourceDialogController.class, form -> form.edit(viewModel.owner(), resource))
                    .ifPresent(viewModel::apply);
        }

        @Override
        public void delete(ResourceView resource) {
            if (confirmations.confirmDelete(window(), "resource", resource.title(), null)) {
                viewModel.removeResource(resource.id());
            }
        }
    }

    // ---- exercises ----

    @FXML
    private void addExercise() {
        if (viewModel.addExercise(exerciseInput.getText())) {
            exerciseInput.clear();
        }
    }

    private final class ExerciseActions implements ExerciseCell.Actions {

        @Override
        public void markDone(ExerciseView exercise, boolean done) {
            viewModel.markExerciseDone(exercise.id(), done);
        }

        @Override
        public void moveUp(ExerciseView exercise) {
            viewModel.moveExercise(exercise.id(), MoveDirection.UP);
        }

        @Override
        public void moveDown(ExerciseView exercise) {
            viewModel.moveExercise(exercise.id(), MoveDirection.DOWN);
        }

        @Override
        public void edit(ExerciseView exercise) {
            dialogs.show(window(), ExerciseDialogController.class, form -> form.edit(viewModel.owner(), exercise))
                    .ifPresent(viewModel::apply);
        }

        @Override
        public void delete(ExerciseView exercise) {
            if (confirmations.confirmDelete(window(), "exercise", exercise.title(), null)) {
                viewModel.removeExercise(exercise.id());
            }
        }
    }

    // ---- results ----

    @FXML
    private void addResult() {
        dialogs.show(window(), ResultDialogController.class, form -> form.create(viewModel.owner(), viewModel.goalId()))
                .ifPresent(viewModel::apply);
    }

    private void editResult(ResultView result) {
        dialogs.show(window(), ResultDialogController.class, form -> form.edit(viewModel.owner(), viewModel.goalId(), result))
                .ifPresent(viewModel::apply);
    }

    private void deleteResult(ResultView result) {
        if (confirmations.confirmDelete(window(), "result", result.title(), null)) {
            viewModel.removeResult(result.id());
        }
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
