package com.edutrack.desktop.ui.goal;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.ActionResult;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.DeleteConsequences;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.dialog.GoalDialogController;
import com.edutrack.desktop.ui.dialog.ResultDialogController;
import com.edutrack.desktop.ui.dialog.SaveTemplateDialogController;
import com.edutrack.desktop.ui.dialog.StepDialogController;
import com.edutrack.desktop.ui.record.ResultRow;
import com.edutrack.desktop.ui.roadmap.StepTimelineCell;
import com.edutrack.desktop.ui.step.StepPageController;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.RecordSummary;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Binds {@code GoalDetailView.fxml} to {@link GoalDetailViewModel}: the goal's header, progress
 * results and roadmap timeline, with the step page of the opened roadmap step on top of it. Dialogs and confirmations are opened here; every change goes through the
 * view model.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GoalDetailController {

    private final GoalDetailViewModel viewModel;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final ViewLoader viewLoader;
    private final GoalExportViewModel exporter;
    private final Messages messages;
    private final ObjectProvider<GoalPanel> panelBeans;
    private final List<GoalPanel> panels = new ArrayList<>();

    @FXML
    private StackPane root;
    @FXML
    private VBox panel;
    @FXML
    private Label titleLabel;
    @FXML
    private MenuButton goalOptions;
    @FXML
    private Label actionStatusLabel;
    @FXML
    private Label deadlineLabel;
    @FXML
    private Label descriptionLabel;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label progressLabel;
    @FXML
    private Label effortLabel;
    @FXML
    private HBox completionBanner;
    @FXML
    private Label completionLabel;
    @FXML
    private TabPane goalTabs;
    @FXML
    private Tab resultsTab;
    @FXML
    private Button addResultButton;
    @FXML
    private VBox goalResults;
    @FXML
    private Button addStepButton;
    @FXML
    private ListView<StepView> timeline;

    // What the panel showed last, to tell "the same goal changed" from "another goal was opened".
    private GoalId shownGoal;
    private StepPageController stepPage;
    private double shownProgress;
    private boolean shownCompleted;

    public GoalDetailController(GoalDetailViewModel viewModel, FormDialogs dialogs, Confirmations confirmations,
                                ViewLoader viewLoader, ObjectProvider<GoalPanel> panelBeans,
                                GoalExportViewModel exporter, Messages messages) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.viewLoader = viewLoader;
        this.panelBeans = panelBeans;
        this.exporter = exporter;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        ViewLoader.LoadedView<StepPageController> stepView = viewLoader.load(StepPageController.class);
        stepPage = stepView.controller();
        Parent stepPageRoot = stepView.root();
        root.getChildren().add(stepPageRoot);
        stepPageRoot.visibleProperty().bind(stepPage.openProperty());
        stepPageRoot.managedProperty().bind(stepPageRoot.visibleProperty());
        stepPage.setOnBack(() -> {
            viewModel.reload();
            Motion.fadeIn(panel);
        });

        panel.visibleProperty().bind(viewModel.hasGoalProperty().and(stepPage.openProperty().not()));
        panel.managedProperty().bind(panel.visibleProperty());

        goalOptions.setGraphic(new FontIcon(Feather.MORE_HORIZONTAL));
        actionStatusLabel.managedProperty().bind(actionStatusLabel.visibleProperty());
        showStatus(null);

        titleLabel.textProperty().bind(viewModel.titleProperty());
        deadlineLabel.textProperty().bind(viewModel.deadlineTextProperty());
        descriptionLabel.textProperty().bind(viewModel.descriptionProperty());
        descriptionLabel.visibleProperty().bind(viewModel.descriptionProperty().isNotEmpty());
        descriptionLabel.managedProperty().bind(descriptionLabel.visibleProperty());
        progressLabel.textProperty().bind(viewModel.progressTextProperty());
        effortLabel.textProperty().bind(viewModel.effortTextProperty());

        completionLabel.textProperty().bind(viewModel.completionTextProperty());
        completionBanner.visibleProperty().bind(viewModel.completedProperty());
        completionBanner.managedProperty().bind(completionBanner.visibleProperty());
        completionBanner.getChildren().addFirst(new FontIcon(Feather.CHECK_CIRCLE));

        addStepButton.setGraphic(new FontIcon(Feather.PLUS));
        addResultButton.setGraphic(new FontIcon(Feather.PLUS));
        viewModel.results().addListener((ListChangeListener<RecordView.ResultView>) change -> renderResults());
        renderResults();

        // Practice, skills, cards and evidence go between the roadmap and the results.
        panelBeans.orderedStream().forEach(panelBean -> {
            Tab tab = new Tab();
            tab.setClosable(false);
            tab.textProperty().bind(panelBean.title());
            tab.setContent(panelBean.content());
            goalTabs.getTabs().add(goalTabs.getTabs().indexOf(resultsTab), tab);
            panels.add(panelBean);
        });

        timeline.setItems(viewModel.steps());
        timeline.setCellFactory(StepTimelineCell.factory(new StepActions()));

        // Subscribed after the view model's own subscription, so its progress and completion values are current.
        viewModel.goalProperty().subscribe(this::onGoalChanged);
    }

    /** True while the page of a roadmap step covers the goal page. */
    public ReadOnlyBooleanProperty stepOpenProperty() {
        return stepPage.openProperty();
    }

    /** The area page is going away: panels stop following the data. */
    public void dispose() {
        panels.forEach(GoalPanel::clear);
    }

    public void show(GoalId goalId) {
        // Switching goal cards saves the notes and returns to a goal page — unless the notes could
        // not be saved: then the step page stays, showing why, so nothing typed is lost.
        if (stepPage.openProperty().get() && !stepPage.close()) {
            return;
        }
        viewModel.show(goalId);
    }

    /**
     * Opens the page of one roadmap step of the shown goal, saving the notes of a step page that is
     * already open first. Does nothing when the goal has no such step or the notes could not be saved.
     */
    public void openStep(StepId stepId) {
        GoalDetails goal = viewModel.goalProperty().get();
        if (goal == null || goal.steps().stream().noneMatch(step -> step.id().equals(stepId))) {
            return;
        }
        if (stepPage.openProperty().get() && !stepPage.close()) {
            return;
        }
        stepPage.open(goal, stepId);
        Motion.fadeIn(stepPage.root());
    }

    /** One row per result of the goal, or a hint while there are none. */
    private void renderResults() {
        goalResults.getChildren().clear();
        if (viewModel.results().isEmpty()) {
            Label hint = new Label(I18n.get("goal.results.hint"));
            hint.getStyleClass().add("text-caption");
            goalResults.getChildren().add(hint);
            return;
        }
        for (RecordView.ResultView result : viewModel.results()) {
            goalResults.getChildren().add(ResultRow.build(result, () -> editResult(result),
                    () -> deleteResult(result), null));
        }
    }

    @FXML
    private void addResult() {
        dialogs.show(window(), ResultDialogController.class, form -> form.create(viewModel.resultsOwner(),
                viewModel.goalProperty().get().id()))
                .ifPresent(viewModel::applyResults);
    }

    private void editResult(RecordView.ResultView result) {
        dialogs.show(window(), ResultDialogController.class, form -> form.edit(viewModel.resultsOwner(),
                viewModel.goalProperty().get().id(), result))
                .ifPresent(viewModel::applyResults);
    }

    private void deleteResult(RecordView.ResultView result) {
        if (confirmations.confirmDelete(window(), "result", result.title(), null)) {
            viewModel.removeResult(result.id());
        }
    }

    /** Opening another goal sets the bar at once; changes to the open goal glide and announce completion. */
    private void onGoalChanged(GoalDetails goal) {
        GoalId id = goal == null ? null : goal.id();
        double progress = viewModel.progressProperty().get();
        boolean completed = viewModel.completedProperty().get();
        if (goal != null) {
            if (!id.equals(shownGoal)) {
                showStatus(null);
                Motion.setProgress(progressBar, progress);
            } else {
                if (Double.compare(progress, shownProgress) != 0) {
                    Motion.animateProgress(progressBar, progress);
                }
                if (completed && !shownCompleted) {
                    Motion.fadeIn(completionBanner);
                }
            }
        }
        if (goal != null) {
            panels.forEach(panel -> panel.show(goal));
        } else {
            panels.forEach(GoalPanel::clear);
        }
        shownGoal = id;
        shownProgress = progress;
        shownCompleted = completed;
    }

    @FXML
    private void exportNotes() {
        export(GoalExportViewModel.Kind.NOTES);
    }

    @FXML
    private void exportCards() {
        export(GoalExportViewModel.Kind.CARDS);
    }

    @FXML
    private void exportResults() {
        export(GoalExportViewModel.Kind.RESULTS);
    }

    private void export(GoalExportViewModel.Kind kind) {
        GoalDetails goal = viewModel.goalProperty().get();
        FileChooser chooser = new FileChooser();
        chooser.setTitle(exporter.chooserTitle(kind));
        chooser.setInitialFileName(exporter.suggestedFileName(kind, goal.title()));
        String filter = messages.get("goal.export.filter." + kind.extension());
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(filter, "*." + kind.extension()));
        File file = chooser.showSaveDialog(window());
        if (file != null) {
            showStatus(exporter.export(kind, goal.id(), file.toPath()));
        }
    }

    @FXML
    private void saveAsTemplate() {
        GoalDetails goal = viewModel.goalProperty().get();
        dialogs.show(window(), SaveTemplateDialogController.class, form -> form.create(goal.id(), goal.title()))
                .ifPresent(template -> showStatus(ActionResult.ok(messages.get("goal.template.saved", template.name()))));
    }

    /** Shows the outcome of the last export or template action; {@code null} hides it. */
    private void showStatus(ActionResult result) {
        actionStatusLabel.getStyleClass().remove("form-error");
        actionStatusLabel.setVisible(result != null);
        actionStatusLabel.setText(result == null ? "" : result.message());
        if (result != null && !result.success()) {
            actionStatusLabel.getStyleClass().add("form-error");
        }
    }

    @FXML
    private void editGoal() {
        dialogs.show(window(), GoalDialogController.class, form -> form.edit(viewModel.goalProperty().get()))
                .ifPresent(viewModel::apply);
    }

    @FXML
    private void deleteGoal() {
        GoalDetails goal = viewModel.goalProperty().get();
        RecordSummary recorded = goal.steps().stream().map(StepView::record)
                .reduce(RecordSummary.EMPTY, RecordSummary::plus)
                .plus(new RecordSummary(false, 0, 0, 0, viewModel.results().size()));
        String consequence = DeleteConsequences.ofGoal(goal.steps().size(), recorded);
        if (confirmations.confirmDelete(window(), "goal", goal.title(), consequence)) {
            viewModel.deleteGoal();
        }
    }

    @FXML
    private void addStep() {
        GoalId goalId = viewModel.goalProperty().get().id();
        dialogs.show(window(), StepDialogController.class, form -> form.create(goalId)).ifPresent(details -> {
            viewModel.apply(details);
            timeline.scrollTo(timeline.getItems().size() - 1);
        });
    }

    /** The items are replaced on every change; selecting the step again keeps keyboard users in place. */
    private void reselect(StepId stepId) {
        List<StepView> steps = timeline.getItems();
        for (int index = 0; index < steps.size(); index++) {
            if (steps.get(index).id().equals(stepId)) {
                timeline.getSelectionModel().select(index);
                timeline.getFocusModel().focus(index);
                return;
            }
        }
    }

    private Window window() {
        return panel.getScene().getWindow();
    }

    /** What the timeline rows can ask for. */
    private final class StepActions implements StepTimelineCell.Actions {

        @Override
        public void cycleStatus(StepView step) {
            if (step.status() == StepStatus.DONE && !confirmations.confirmReopen(window(), step.title(),
                    Formats.date(step.completedAt(), ZoneId.systemDefault()))) {
                return;
            }
            viewModel.cycleStatus(step.id());
            reselect(step.id());
        }

        @Override
        public void moveUp(StepView step) {
            viewModel.moveStep(step.id(), MoveDirection.UP);
            reselect(step.id());
        }

        @Override
        public void moveDown(StepView step) {
            viewModel.moveStep(step.id(), MoveDirection.DOWN);
            reselect(step.id());
        }

        @Override
        public void edit(StepView step) {
            GoalId goalId = viewModel.goalProperty().get().id();
            dialogs.show(window(), StepDialogController.class, form -> form.edit(goalId, step))
                    .ifPresent(viewModel::apply);
        }

        @Override
        public void delete(StepView step) {
            if (confirmations.confirmDelete(window(), "step", step.title(),
                    DeleteConsequences.ofStep(step.record()))) {
                viewModel.removeStep(step.id());
            }
        }

        @Override
        public void open(StepView step) {
            openStep(step.id());
        }
    }
}
