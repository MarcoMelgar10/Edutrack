package com.edutrack.desktop.ui.practice;

import atlantafx.base.theme.Styles;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.goal.GoalPanel;
import com.edutrack.desktop.ui.session.GoalSessionsDialog;
import com.edutrack.desktop.ui.session.LogSessionDialogController;
import com.edutrack.desktop.ui.session.SessionLauncher;
import com.edutrack.desktop.ui.session.SessionTimer;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.session.SessionSource;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitMenuButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * The Practice tab of a goal: time logged, the pace against the plan, and the practice routines with
 * buttons to log or time each one.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Order(10)
public class PracticePanel implements GoalPanel {

    private final PracticeViewModel viewModel;
    private final SessionLauncher launcher;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final GoalSessionsDialog allSessions;
    private final Messages messages;
    private final Clock clock;

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();
    private final ScrollPane content = new ScrollPane();
    private final VBox routineBox = new VBox();

    public PracticePanel(PracticeViewModel viewModel, SessionLauncher launcher, FormDialogs dialogs,
                         Confirmations confirmations, GoalSessionsDialog allSessions, Messages messages, Clock clock) {
        this.viewModel = viewModel;
        this.launcher = launcher;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.allSessions = allSessions;
        this.messages = messages;
        this.clock = clock;
        title.set(messages.get("goal.tab.practice"));
        build();
    }

    private void build() {
        // Time
        Label timeLabel = new Label();
        timeLabel.getStyleClass().add("practice-time");
        timeLabel.textProperty().bind(viewModel.timeTextProperty());
        timeLabel.setWrapText(true);

        SplitMenuButton start = new SplitMenuButton();
        start.setText(messages.get("session.start"));
        start.getStyleClass().add(Styles.ACCENT);
        start.setGraphic(new FontIcon(Feather.PLAY));
        start.setOnAction(click -> launcher.start(window(), goalId(), null, SessionTimer.Mode.FREE));
        MenuItem pomodoro = new MenuItem(messages.get("session.startPomodoro"));
        pomodoro.setOnAction(click -> launcher.start(window(), goalId(), null, SessionTimer.Mode.POMODORO));
        MenuItem startOnStep = new MenuItem(messages.get("practice.startOnStep"));
        startOnStep.setOnAction(click -> launcher.pickAndStart(window(), goalId(), null));
        start.getItems().addAll(pomodoro, startOnStep);

        Button log = new Button(messages.get("session.log"));
        log.setOnAction(click -> dialogs.show(window(), LogSessionDialogController.class,
                form -> form.create(goalId(), null)));
        Button all = new Button(messages.get("practice.allSessions"));
        all.getStyleClass().add(Styles.FLAT);
        all.setOnAction(click -> allSessions.show(window(), goalId(), viewModel.goal().title()));
        HBox timeButtons = new HBox(8, start, log, all);
        timeButtons.setAlignment(Pos.CENTER_LEFT);

        // Pace
        Label paceLabel = new Label();
        paceLabel.getStyleClass().add("practice-pace");
        paceLabel.setWrapText(true);
        paceLabel.textProperty().bind(viewModel.paceTextProperty());
        viewModel.paceBehindProperty().subscribe(behind -> {
            paceLabel.setGraphic(new FontIcon(behind ? Feather.ALERT_CIRCLE : Feather.CHECK_CIRCLE));
            paceLabel.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("behind"), behind);
        });
        paceLabel.visibleProperty().bind(viewModel.paceVisibleProperty());
        paceLabel.managedProperty().bind(paceLabel.visibleProperty());

        // Routines
        Label routinesTitle = new Label();
        routinesTitle.getStyleClass().add("section-label");
        routinesTitle.textProperty().bind(viewModel.routinesTitleProperty());
        Button addRoutine = new Button(messages.get("practice.routines.add"));
        addRoutine.setGraphic(new FontIcon(Feather.PLUS));
        addRoutine.setOnAction(click -> dialogs.show(window(), RoutineDialogController.class,
                form -> form.create(goalId())));
        HBox routinesHeader = new HBox(routinesTitle, spacer(), addRoutine);
        routinesHeader.setAlignment(Pos.CENTER_LEFT);

        Label routinesHint = new Label(messages.get("practice.routines.hint"));
        routinesHint.getStyleClass().add("text-caption");
        routinesHint.setWrapText(true);

        routineBox.getStyleClass().add("routine-list");
        viewModel.routines().addListener((ListChangeListener<RoutineRow>) change -> renderRoutines());

        Label timeTitle = new Label(messages.get("practice.time.title"));
        timeTitle.getStyleClass().add("section-label");

        VBox body = new VBox(10, timeTitle, timeLabel, timeButtons, paceLabel, routinesHeader, routinesHint, routineBox);
        body.getStyleClass().add("goal-tab-body");
        body.setPadding(new Insets(12, 4, 24, 0));
        content.setContent(body);
        content.setFitToWidth(true);
        content.getStyleClass().add("goal-tab-scroll");
    }

    private void renderRoutines() {
        routineBox.getChildren().clear();
        for (RoutineRow row : viewModel.routines()) {
            routineBox.getChildren().add(routineNode(row));
        }
    }

    private Node routineNode(RoutineRow row) {
        RoutineView routine = row.routine();
        FontIcon mark = new FontIcon(row.doneToday() ? Feather.CHECK_CIRCLE : Feather.CIRCLE);
        mark.getStyleClass().add("routine-mark");

        Label name = new Label(routine.title());
        name.getStyleClass().add("routine-title");
        String caption = row.cadenceText() + (routine.stepTitle() == null ? "" : " · " + routine.stepTitle());
        Label detail = new Label(caption);
        detail.getStyleClass().add("text-caption");
        VBox text = new VBox(2, name, detail);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label week = new Label(row.weekText());
        week.getStyleClass().add(row.metThisWeek() ? "routine-week-met" : "text-caption");

        Button log = iconButton(Feather.CLOCK, messages.get("practice.routine.log"));
        log.setOnAction(click -> logRoutine(routine));
        log.setDisable(!routine.active());
        Button timer = iconButton(Feather.PLAY, messages.get("practice.routine.start"));
        timer.setOnAction(click -> launcher.startRoutine(window(), routine, viewModel.goal().title()));
        timer.setDisable(!routine.active());
        Button more = iconButton(Feather.MORE_HORIZONTAL, messages.get("practice.routine.more"));
        ContextMenu menu = menuFor(routine);
        more.setOnAction(click -> menu.show(more, javafx.geometry.Side.BOTTOM, 0, 0));

        HBox line = new HBox(10, mark, text, week, log, timer, more);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("routine-row");
        line.setOnContextMenuRequested(event -> menu.show(line, event.getScreenX(), event.getScreenY()));
        return line;
    }

    private ContextMenu menuFor(RoutineView routine) {
        MenuItem edit = new MenuItem(messages.get("common.edit"));
        edit.setOnAction(click -> dialogs.show(window(), RoutineDialogController.class, form -> form.edit(routine)));
        MenuItem pause = new MenuItem(messages.get(routine.active() ? "practice.routine.pause" : "practice.routine.resume"));
        pause.setOnAction(click -> {
            if (routine.active()) {
                viewModel.pause(routine.id());
            } else {
                viewModel.resume(routine.id());
            }
        });
        MenuItem up = new MenuItem(messages.get("common.moveUp"));
        up.setOnAction(click -> viewModel.move(routine.id(), MoveDirection.UP));
        MenuItem down = new MenuItem(messages.get("common.moveDown"));
        down.setOnAction(click -> viewModel.move(routine.id(), MoveDirection.DOWN));
        MenuItem delete = new MenuItem(messages.get("common.delete"));
        delete.setOnAction(click -> {
            if (confirmations.confirmDelete(window(), "routine", routine.title(),
                    messages.get("practice.routine.deleteNote"))) {
                viewModel.delete(routine.id());
            }
        });
        return new ContextMenu(edit, pause, up, down, delete);
    }

    /** Opens the log dialog filled in with the routine's usual time, ending now. */
    private void logRoutine(RoutineView routine) {
        Instant start = clock.instant().minusSeconds(routine.targetMinutes() * 60L);
        dialogs.show(window(), LogSessionDialogController.class, form -> form.prefill(routine.goalId(),
                routine.stepId(), routine.id(), start, routine.targetMinutes(), SessionSource.MANUAL));
    }

    private Button iconButton(Feather icon, String tip) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().addAll("icon-button");
        button.setTooltip(new Tooltip(tip));
        button.setAccessibleText(tip);
        return button;
    }

    private static Region spacer() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return spacer;
    }

    private com.edutrack.domain.goal.GoalId goalId() {
        return viewModel.goal().id();
    }

    private Window window() {
        return content.getScene().getWindow();
    }

    // ---- GoalPanel ----

    @Override
    public ObservableValue<String> title() {
        return title.getReadOnlyProperty();
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public void show(GoalDetails goal) {
        viewModel.show(goal);
    }

    @Override
    public void clear() {
        viewModel.clear();
    }

    ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }
}
