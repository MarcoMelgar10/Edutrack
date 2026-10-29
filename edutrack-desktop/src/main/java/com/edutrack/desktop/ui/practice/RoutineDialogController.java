package com.edutrack.desktop.ui.practice;

import com.edutrack.application.practice.RoutineView;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.desktop.ui.session.StepChoice;
import com.edutrack.domain.goal.GoalId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code RoutineDialogView.fxml} to {@link RoutineDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RoutineDialogController implements DialogForm<RoutineView> {

    private final RoutineDialogViewModel viewModel;

    @FXML
    private TextField titleField;
    @FXML
    private ComboBox<StepChoice> stepBox;
    @FXML
    private ComboBox<RoutineDialogViewModel.CadenceChoice> cadenceBox;
    @FXML
    private VBox timesField;
    @FXML
    private TextField timesInput;
    @FXML
    private TextField minutesInput;
    @FXML
    private Label errorLabel;

    public RoutineDialogController(RoutineDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        titleField.textProperty().bindBidirectional(viewModel.titleProperty());
        stepBox.setItems(viewModel.steps());
        stepBox.valueProperty().bindBidirectional(viewModel.stepProperty());
        cadenceBox.setItems(viewModel.cadences());
        cadenceBox.valueProperty().bindBidirectional(viewModel.cadenceProperty());
        timesInput.textProperty().bindBidirectional(viewModel.timesPerWeekProperty());
        timesField.visibleProperty().bind(viewModel.timesVisibleProperty());
        timesField.managedProperty().bind(timesField.visibleProperty());
        minutesInput.textProperty().bindBidirectional(viewModel.minutesProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    public void create(GoalId goalId) {
        viewModel.create(goalId);
    }

    public void edit(RoutineView routine) {
        viewModel.edit(routine);
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
    public RoutineView submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
