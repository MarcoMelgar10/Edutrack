package com.edutrack.desktop.ui.area;

import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.desktop.ui.common.Reorderable;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.dialog.AreaDialogController;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.DeleteConsequences;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.dialog.GoalDialogController;
import com.edutrack.desktop.ui.dialog.TemplatePickerController;
import com.edutrack.desktop.ui.goal.GoalDetailController;
import com.edutrack.desktop.ui.shell.FocusMode;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import javafx.application.Platform;
import javafx.beans.binding.BooleanBinding;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Binds {@code AreaTabView.fxml} to {@link AreaTabViewModel}: the goal cards on the left and the
 * {@link GoalDetailController goal panel} for the selected card on the right.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AreaTabController {

    private final AreaTabViewModel viewModel;
    private final ViewLoader viewLoader;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final Clock clock;
    private final FocusMode focusMode;

    @FXML
    private SplitPane split;
    @FXML
    private VBox goalListPane;
    @FXML
    private Label headerTitle;
    @FXML
    private Label headerProgress;
    @FXML
    private MenuButton areaOptions;
    @FXML
    private Button newGoalButton;
    @FXML
    private ListView<GoalSummary> goalList;
    @FXML
    private StackPane detailHost;
    @FXML
    private VBox emptyState;

    private GoalDetailController goalDetail;
    private boolean syncingSelection;
    private BooleanBinding goalListHidden;
    private double dividerPosition = 0.32;

    public AreaTabController(AreaTabViewModel viewModel, ViewLoader viewLoader, FormDialogs dialogs,
                             Confirmations confirmations, Clock clock, FocusMode focusMode) {
        this.viewModel = viewModel;
        this.viewLoader = viewLoader;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.clock = clock;
        this.focusMode = focusMode;
    }

    @FXML
    private void initialize() {
        headerTitle.textProperty().bind(viewModel.headerTitleProperty());
        headerProgress.textProperty().bind(viewModel.headerProgressProperty());
        areaOptions.setGraphic(new FontIcon(Feather.MORE_HORIZONTAL));
        newGoalButton.setGraphic(new FontIcon(Feather.PLUS));

        ViewLoader.LoadedView<GoalDetailController> detail = viewLoader.load(GoalDetailController.class);
        goalDetail = detail.controller();
        detailHost.getChildren().addFirst(detail.root());
        // The goal list gives its room to the open step page (when asked) and to focus mode.
        goalListHidden = focusMode.goalListHidden(goalDetail.stepOpenProperty());
        goalListHidden.subscribe(this::setGoalListHidden);
        emptyState.visibleProperty().bind(viewModel.selectedGoalProperty().isNull());
        emptyState.managedProperty().bind(emptyState.visibleProperty());

        // The goals list is replaced with setAll on every refresh. The list view reacts to that by
        // selecting whatever now sits at the old index, so this listener is registered BEFORE the
        // list view takes the items (listeners run in registration order) to mute the selection
        // listener until the list view is done, and the second one then restores the real selection.
        viewModel.goals().addListener((ListChangeListener<GoalSummary>) change -> syncingSelection = true);
        goalList.setItems(viewModel.goals());
        Reorderable.Mover<GoalSummary> mover = new GoalMover();
        goalList.setCellFactory(list -> new GoalCardCell(() -> LocalDate.now(clock), mover));
        Reorderable.installKeys(goalList, mover);
        viewModel.goals().addListener((ListChangeListener<GoalSummary>) change -> {
            selectCurrentGoal();
            syncingSelection = false;
        });

        goalList.getSelectionModel().selectedItemProperty().subscribe(selected -> {
            if (!syncingSelection && selected != null) {
                viewModel.select(selected.id());
            }
        });
        viewModel.selectedGoalProperty().subscribe(id -> {
            goalDetail.show(id);
            selectCurrentGoal();
        });
    }

    private void setGoalListHidden(boolean hidden) {
        boolean showing = split.getItems().contains(goalListPane);
        if (hidden == !showing) {
            return;
        }
        if (hidden) {
            dividerPosition = split.getDividerPositions()[0];
            split.getItems().remove(goalListPane);
        } else {
            split.getItems().addFirst(goalListPane);
            Platform.runLater(() -> split.setDividerPositions(dividerPosition));
        }
        if (split.getScene() != null) {
            Motion.fadeIn(detailHost);
        }
    }

    public void show(AreaId areaId) {
        viewModel.show(areaId);
    }

    public void dispose() {
        goalDetail.dispose();
        viewModel.dispose();
    }

    /** Selects a goal of this area and shows it in the detail panel. */
    public void openGoal(GoalId goalId) {
        viewModel.select(goalId);
    }

    /** Selects a goal of this area and opens the page of one of its roadmap steps. */
    public void openStep(GoalId goalId, StepId stepId) {
        viewModel.select(goalId);
        goalDetail.openStep(stepId);
    }

    /** Reordering of the goal cards; afterwards the list reloads and the moved goal stays selected. */
    private final class GoalMover implements Reorderable.Mover<GoalSummary> {

        @Override
        public void step(GoalSummary goal, int direction) {
            viewModel.select(goal.id());
            viewModel.moveGoal(goal.id(), direction < 0 ? MoveDirection.UP : MoveDirection.DOWN);
        }

        @Override
        public void moveTo(GoalSummary goal, int index) {
            viewModel.select(goal.id());
            viewModel.moveGoalTo(goal.id(), index);
        }
    }

    /** Makes the list view's selection match the view model's selected goal. */
    private void selectCurrentGoal() {
        GoalId selected = viewModel.selectedGoalProperty().get();
        boolean before = syncingSelection;
        syncingSelection = true;
        try {
            goalList.getItems().stream()
                    .filter(goal -> goal.id().equals(selected))
                    .findFirst()
                    .ifPresentOrElse(goalList.getSelectionModel()::select, goalList.getSelectionModel()::clearSelection);
        } finally {
            syncingSelection = before;
        }
    }

    @FXML
    private void newGoal() {
        dialogs.show(window(), GoalDialogController.class, form -> form.create(viewModel.areaId()))
                .ifPresent(details -> viewModel.select(details.id()));
    }

    @FXML
    private void newGoalFromTemplate() {
        dialogs.show(window(), TemplatePickerController.class, form -> form.create(viewModel.areaId()))
                .ifPresent(viewModel::select);
    }

    @FXML
    private void editArea() {
        dialogs.show(window(), AreaDialogController.class, form -> form.edit(viewModel.areaProperty().get()));
    }

    @FXML
    private void deleteArea() {
        AreaSummary area = viewModel.areaProperty().get();
        String consequence = DeleteConsequences.ofArea(area.goalCount());
        if (confirmations.confirmDelete(window(), "area", area.name(), consequence)) {
            viewModel.deleteArea();
        }
    }

    private Window window() {
        return detailHost.getScene().getWindow();
    }
}
