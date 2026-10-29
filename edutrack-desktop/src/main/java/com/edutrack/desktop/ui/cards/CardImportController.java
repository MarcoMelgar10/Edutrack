package com.edutrack.desktop.ui.cards;

import com.edutrack.application.review.ImportResult;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.StepChoice;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;

/** Binds {@code CardImportView.fxml} to {@link CardImportViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CardImportController implements DialogForm<ImportResult> {

    private final CardImportViewModel viewModel;
    private final Messages messages;

    @FXML
    private TextArea textArea;
    @FXML
    private ComboBox<StepChoice> stepBox;
    @FXML
    private Label errorLabel;

    public CardImportController(CardImportViewModel viewModel, Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        textArea.textProperty().bindBidirectional(viewModel.textProperty());
        stepBox.setItems(viewModel.steps());
        stepBox.valueProperty().bindBidirectional(viewModel.stepProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(textArea::requestFocus);
    }

    public void create(GoalId goalId, StepId stepId) {
        viewModel.create(goalId, stepId);
    }

    @FXML
    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("cards.import.chooseTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(messages.get("cards.import.fileType"), "*.txt", "*.tsv", "*.csv"),
                new FileChooser.ExtensionFilter(messages.get("cards.import.anyFile"), "*.*"));
        File file = chooser.showOpenDialog(textArea.getScene().getWindow());
        if (file != null) {
            viewModel.load(file.toPath());
        }
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
    public ImportResult submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
