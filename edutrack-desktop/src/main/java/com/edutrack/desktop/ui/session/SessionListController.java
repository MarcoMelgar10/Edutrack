package com.edutrack.desktop.ui.session;

import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Binds {@code SessionListView.fxml} to {@link SessionListViewModel}: the total and the list of logged sessions
 * with add, edit and delete, plus (for a single step) the Start session button. It is the Sessions tab of the
 * step page, and — shown for a whole goal — the content of the "all sessions" dialog.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SessionListController {

    private final SessionListViewModel viewModel;
    private final SessionLauncher launcher;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final Clock clock;
    private final Messages messages;

    @FXML
    private VBox root;
    @FXML
    private HBox startRow;
    @FXML
    private SplitMenuButton startButton;
    @FXML
    private Label totalLabel;
    @FXML
    private ListView<SessionView> sessionList;

    public SessionListController(SessionListViewModel viewModel, SessionLauncher launcher, FormDialogs dialogs,
                                 Confirmations confirmations, Clock clock, Messages messages) {
        this.viewModel = viewModel;
        this.launcher = launcher;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.clock = clock;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        totalLabel.textProperty().bind(viewModel.totalTextProperty());
        sessionList.setItems(viewModel.sessions());
        startButton.setGraphic(new FontIcon(Feather.PLAY));
        startButton.setAccessibleText(messages.get("session.start"));
    }

    /** Shows the sessions of one step, with the buttons that start or log a session on it. */
    public void showStep(GoalId goalId, StepId stepId) {
        setRowsForStep(true);
        viewModel.showStep(goalId, stepId);
    }

    /** Shows the sessions of a whole goal (each row names its step); only editing and deleting are offered. */
    public void showGoal(GoalId goalId) {
        setRowsForStep(false);
        viewModel.showGoal(goalId);
    }

    /** Stops following the data; the step page was closed. */
    public void clear() {
        viewModel.clear();
    }

    public ReadOnlyStringProperty tabTextProperty() {
        return viewModel.tabTextProperty();
    }

    public VBox root() {
        return root;
    }

    private void setRowsForStep(boolean stepMode) {
        startRow.setVisible(stepMode);
        startRow.setManaged(stepMode);
        sessionList.setCellFactory(SessionCell.factory(!stepMode, clock.getZone(), this::edit, this::delete));
    }

    // ---- actions ----

    @FXML
    private void startSession() {
        launcher.start(window(), viewModel.goalId(), viewModel.stepId(), SessionTimer.Mode.FREE);
    }

    @FXML
    private void startPomodoro() {
        launcher.start(window(), viewModel.goalId(), viewModel.stepId(), SessionTimer.Mode.POMODORO);
    }

    @FXML
    private void logSession() {
        GoalId goalId = viewModel.goalId();
        StepId stepId = viewModel.stepId();
        dialogs.show(window(), LogSessionDialogController.class, form -> form.create(goalId, stepId));
    }

    private void edit(SessionView session) {
        dialogs.show(window(), LogSessionDialogController.class, form -> form.edit(session));
    }

    private void delete(SessionView session) {
        String name = Formats.date(session.startedAt(), clock.getZone()) + " · " + SessionFormats.duration(session.minutes());
        if (confirmations.confirmDelete(window(), "session", name, null)) {
            viewModel.delete(session.id());
        }
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
