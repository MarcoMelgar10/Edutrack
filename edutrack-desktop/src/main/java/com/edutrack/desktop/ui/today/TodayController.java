package com.edutrack.desktop.ui.today;

import atlantafx.base.theme.Styles;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.AreaDialogController;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.session.LogSessionDialogController;
import com.edutrack.desktop.ui.session.SessionFormats;
import com.edutrack.desktop.ui.session.SessionLauncher;
import com.edutrack.desktop.ui.session.SessionTimer;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.desktop.ui.shell.PageLifecycle;
import com.edutrack.desktop.ui.shell.ShellPage;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Binds {@code TodayView.fxml} to {@link TodayViewModel}: the date and streak, the first-run welcome, and
 * the sections of what to do now (check-in, review, routines, this week, goals behind pace, goals in
 * progress), each shown only when it has something.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TodayController implements PageLifecycle {

    private final TodayViewModel viewModel;
    private final Navigator navigator;
    private final FormDialogs dialogs;
    private final SessionLauncher launcher;
    private final Messages messages;

    @FXML
    private Label dateLabel;
    @FXML
    private HBox headerRow;
    @FXML
    private Label streakLabel;
    @FXML
    private VBox sections;
    @FXML
    private VBox welcome;
    @FXML
    private VBox goalsSection;
    @FXML
    private VBox goalList;
    @FXML
    private Label noGoalsLabel;

    public TodayController(TodayViewModel viewModel, Navigator navigator, FormDialogs dialogs,
                           SessionLauncher launcher, Messages messages) {
        this.viewModel = viewModel;
        this.navigator = navigator;
        this.dialogs = dialogs;
        this.launcher = launcher;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        dateLabel.textProperty().bind(viewModel.dateTextProperty());
        welcome.visibleProperty().bind(viewModel.noAreasProperty());
        welcome.managedProperty().bind(welcome.visibleProperty());
        goalsSection.visibleProperty().bind(viewModel.noAreasProperty().not());
        goalsSection.managedProperty().bind(goalsSection.visibleProperty());
        headerRow.visibleProperty().bind(viewModel.noAreasProperty().not());
        headerRow.managedProperty().bind(headerRow.visibleProperty());

        streakLabel.setGraphic(new FontIcon(Feather.ZAP));
        streakLabel.textProperty().bind(viewModel.streakTextProperty());

        viewModel.goals().addListener((ListChangeListener<TodayViewModel.GoalRow>) change -> rebuildRows());
        viewModel.routines().addListener((ListChangeListener<TodayViewModel.RoutineItem>) change -> rebuildSections());
        viewModel.agenda().addListener((ListChangeListener<TodayViewModel.AgendaRow>) change -> rebuildSections());
        viewModel.attention().addListener((ListChangeListener<TodayViewModel.AttentionRow>) change -> rebuildSections());
        viewModel.checkInDueProperty().subscribe(due -> rebuildSections());
        viewModel.reviewDueProperty().subscribe(due -> rebuildSections());
        viewModel.reviewTextProperty().subscribe(text -> rebuildSections());
        rebuildRows();
        rebuildSections();
    }

    @Override
    public void shown() {
        viewModel.refresh(); // the date may have changed since the page was last shown
    }

    @Override
    public void disposed() {
        viewModel.dispose();
    }

    @FXML
    private void addSuggestedAreas() {
        viewModel.addSuggestedAreas();
    }

    @FXML
    private void newArea() {
        dialogs.show(window(), AreaDialogController.class, form -> {
        }).ifPresent(created -> navigator.openArea(created.id()));
    }

    @FXML
    private void startSession() {
        launcher.pickAndStart(window(), null, null);
    }

    @FXML
    private void logSession() {
        dialogs.show(window(), LogSessionDialogController.class, form -> form.create(null, null));
    }

    // ---- sections ----

    private void rebuildSections() {
        sections.getChildren().clear();
        if (viewModel.checkInDueProperty().get()) {
            sections.getChildren().add(callout(Feather.CHECK_SQUARE, messages.get("today.checkin.title"),
                    messages.get("today.checkin.text"), messages.get("today.checkin.action"),
                    navigator::openJournalCheckIn));
        }
        if (viewModel.reviewDueProperty().get()) {
            sections.getChildren().add(callout(Feather.LAYERS, viewModel.reviewTextProperty().get(),
                    messages.get("today.review.hint"), messages.get("today.review.action"),
                    () -> navigator.open(ShellPage.REVIEW)));
        }
        if (!viewModel.routines().isEmpty()) {
            sections.getChildren().add(section(messages.get("today.routines.section"),
                    viewModel.routines().stream().map(this::routineRow).toList()));
        }
        if (!viewModel.agenda().isEmpty()) {
            sections.getChildren().add(section(messages.get("today.agenda.section"),
                    viewModel.agenda().stream().map(this::agendaRow).toList()));
        }
        if (!viewModel.attention().isEmpty()) {
            sections.getChildren().add(section(messages.get("today.attention.section"),
                    viewModel.attention().stream().map(this::attentionRow).toList()));
        }
    }

    private Node section(String title, List<Node> rows) {
        Label label = new Label(title);
        label.getStyleClass().add("section-label");
        VBox box = new VBox(8, label);
        VBox list = new VBox();
        list.getStyleClass().add("area-list");
        list.getChildren().addAll(rows);
        box.getChildren().add(list);
        return box;
    }

    private Node callout(Feather icon, String title, String text, String action, Runnable onAction) {
        Label heading = new Label(title);
        heading.getStyleClass().add("today-callout-title");
        Label body = new Label(text);
        body.getStyleClass().add("text-caption");
        body.setWrapText(true);
        VBox words = new VBox(2, heading, body);
        HBox.setHgrow(words, Priority.ALWAYS);
        Button button = new Button(action);
        button.getStyleClass().add(Styles.ACCENT);
        button.setOnAction(click -> onAction.run());
        HBox box = new HBox(14, new FontIcon(icon), words, button);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("today-callout");
        return box;
    }

    private Node routineRow(TodayViewModel.RoutineItem routine) {
        FontIcon mark = new FontIcon(routine.doneToday() ? Feather.CHECK_CIRCLE : Feather.CIRCLE);
        mark.getStyleClass().add("routine-mark");
        Label title = new Label(routine.title());
        title.getStyleClass().add("area-row-name");
        Label caption = new Label(routine.goalTitle() + " · " + routine.detailText());
        caption.getStyleClass().add("text-caption");
        VBox text = new VBox(2, title, caption);
        HBox.setHgrow(text, Priority.ALWAYS);

        Button log = new Button(messages.get("today.routines.log", SessionFormats.duration(routine.minutes())));
        log.setOnAction(click -> viewModel.logRoutine(routine));
        Button timer = new Button();
        timer.setGraphic(new FontIcon(Feather.PLAY));
        timer.getStyleClass().add("icon-button");
        timer.setTooltip(new Tooltip(messages.get("practice.routine.start")));
        timer.setAccessibleText(messages.get("practice.routine.start"));
        timer.setOnAction(click -> launcher.start(window(), routine.goalId(), routine.stepId(), SessionTimer.Mode.FREE));

        HBox row = new HBox(12, mark, text, log, timer);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("area-row");
        return row;
    }

    private Node agendaRow(TodayViewModel.AgendaRow item) {
        Label title = new Label(item.stepTitle());
        title.getStyleClass().add("area-row-name");
        Label caption = new Label(item.goalTitle());
        caption.getStyleClass().add("text-caption");
        VBox text = new VBox(2, title, caption);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label date = new Label(item.dateText());
        date.getStyleClass().add(item.overdue() ? "today-overdue" : "text-caption");
        if (item.overdue()) {
            date.setGraphic(new FontIcon(Feather.ALERT_CIRCLE));
        }
        HBox row = new HBox(12, text, date);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("area-row");
        makeClickable(row, item.stepTitle() + ", " + item.goalTitle(),
                () -> navigator.openStep(item.goalId(), item.stepId()));
        return row;
    }

    private Node attentionRow(TodayViewModel.AttentionRow item) {
        Label title = new Label(item.title());
        title.getStyleClass().add("area-row-name");
        Label caption = new Label(item.detailText());
        caption.getStyleClass().add("text-caption");
        VBox text = new VBox(2, title, caption);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(12, new FontIcon(Feather.ALERT_CIRCLE), text);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("area-row");
        makeClickable(row, item.title(), () -> navigator.openGoal(item.goalId()));
        return row;
    }

    // ---- goals in progress ----

    private void rebuildRows() {
        goalList.getChildren().setAll(viewModel.goals().stream().map(this::row).toList());
        noGoalsLabel.setVisible(viewModel.goals().isEmpty());
        noGoalsLabel.setManaged(viewModel.goals().isEmpty());
    }

    private Node row(TodayViewModel.GoalRow goal) {
        Label title = new Label(goal.title());
        title.getStyleClass().add("area-row-name");
        Label caption = new Label(messages.get("today.goalCaption", goal.areaName(), goal.deadlineText()));
        caption.getStyleClass().add("text-caption");
        VBox text = new VBox(2, title, caption);
        HBox.setHgrow(text, Priority.ALWAYS);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ProgressBar bar = new ProgressBar(goal.progress());
        bar.getStyleClass().add("area-progress");
        bar.setPrefWidth(180);

        Label percent = new Label(goal.progressText());
        percent.getStyleClass().add("progress-value");
        percent.setMinWidth(44);
        percent.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, text, spacer, bar, percent);
        row.getStyleClass().add("area-row");
        row.setAlignment(Pos.CENTER_LEFT);
        makeClickable(row, goal.title() + ", " + goal.areaName() + ", " + goal.progressText(),
                () -> navigator.openGoal(goal.goalId()));
        return row;
    }

    /** Rows act as buttons: reachable with Tab, opened with a click, Enter or Space. */
    private static void makeClickable(HBox row, String accessibleText, Runnable action) {
        row.setCursor(Cursor.HAND);
        row.setFocusTraversable(true);
        row.setAccessibleText(accessibleText);
        row.setOnMouseClicked(click -> action.run());
        row.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ENTER || key.getCode() == KeyCode.SPACE) {
                action.run();
                key.consume();
            }
        });
    }

    private Window window() {
        return dateLabel.getScene().getWindow();
    }
}
