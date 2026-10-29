package com.edutrack.desktop.ui.session;

import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code StartSessionDialogView.fxml} to {@link StartSessionDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StartSessionDialogController implements DialogForm<StartSessionDialogViewModel.Choice> {

    private final StartSessionDialogViewModel viewModel;

    @FXML
    private ComboBox<GoalChoice> goalBox;
    @FXML
    private ComboBox<StepChoice> stepBox;
    @FXML
    private CheckBox pomodoroCheck;

    public StartSessionDialogController(StartSessionDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        goalBox.setItems(viewModel.goals());
        goalBox.valueProperty().bindBidirectional(viewModel.goalProperty());
        stepBox.setItems(viewModel.steps());
        stepBox.valueProperty().bindBidirectional(viewModel.stepProperty());
        pomodoroCheck.selectedProperty().bindBidirectional(viewModel.pomodoroProperty());
        Platform.runLater(goalBox::requestFocus);
    }

    public void preselect(GoalId goalId, StepId stepId) {
        viewModel.preselect(goalId, stepId);
    }

    @Override
    public String title() {
        return viewModel.title();
    }

    @Override
    public String saveLabel() {
        return viewModel.saveLabel();
    }

    @Override
    public ObservableBooleanValue validProperty() {
        return viewModel.validProperty();
    }

    @Override
    public StartSessionDialogViewModel.Choice submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        // Choosing a goal and a step cannot be rejected by a rule, so there is nothing to show.
    }
}
