package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.domain.goal.GoalId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code SaveTemplateDialogView.fxml} to {@link SaveTemplateDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SaveTemplateDialogController implements DialogForm<GoalTemplate> {

    private final SaveTemplateDialogViewModel viewModel;

    @FXML
    private TextField nameField;
    @FXML
    private Label errorLabel;

    public SaveTemplateDialogController(SaveTemplateDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
        Platform.runLater(() -> {
            nameField.requestFocus();
            nameField.selectAll();
        });
    }

    public void create(GoalId goalId, String goalTitle) {
        viewModel.create(goalId, goalTitle);
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
    public GoalTemplate submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
