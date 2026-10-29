package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.record.RecordView;
import com.edutrack.domain.record.RecordOwner;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code ExerciseDialogView.fxml} to {@link ExerciseDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExerciseDialogController implements DialogForm<RecordView> {

    private final ExerciseDialogViewModel viewModel;

    @FXML
    private TextField titleField;
    @FXML
    private Label errorLabel;

    public ExerciseDialogController(ExerciseDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        titleField.textProperty().bindBidirectional(viewModel.exerciseTitleProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    public void edit(RecordOwner owner, RecordView.ExerciseView exercise) {
        viewModel.edit(owner, exercise);
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
    public RecordView submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
