package com.edutrack.desktop.ui.skills;

import com.edutrack.application.skill.CompetencyView;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.GoalId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code CompetencyDialogView.fxml} to {@link CompetencyDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompetencyDialogController implements DialogForm<CompetencyView> {

    private final CompetencyDialogViewModel viewModel;

    @FXML
    private TextField nameField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private ComboBox<CompetencyDialogViewModel.LevelChoice> targetBox;
    @FXML
    private Label errorLabel;

    public CompetencyDialogController(CompetencyDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionProperty());
        targetBox.setItems(viewModel.levels());
        targetBox.valueProperty().bindBidirectional(viewModel.targetProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(nameField::requestFocus);
    }

    public void create(GoalId goalId) {
        viewModel.create(goalId);
    }

    public void edit(CompetencyView competency) {
        viewModel.edit(competency);
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
    public CompetencyView submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
