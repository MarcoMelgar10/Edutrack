package com.edutrack.desktop.ui.evidence;

import com.edutrack.application.evidence.EvidenceView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.DeadlineConverter;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.desktop.ui.session.StepChoice;
import com.edutrack.domain.evidence.EvidenceKind;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.skill.CompetencyId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Binds {@code EvidenceDialogView.fxml} to {@link EvidenceDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class EvidenceDialogController implements DialogForm<EvidenceView> {

    private final EvidenceDialogViewModel viewModel;
    private final Messages messages;
    private final DeadlineConverter dateConverter = new DeadlineConverter();

    @FXML
    private ComboBox<EvidenceDialogViewModel.Choice<EvidenceKind>> kindBox;
    @FXML
    private TextField titleField;
    @FXML
    private DatePicker datePicker;
    @FXML
    private ComboBox<StepChoice> stepBox;
    @FXML
    private TextField urlField;
    @FXML
    private Label fileLabel;
    @FXML
    private Button removeFileButton;
    @FXML
    private ComboBox<EvidenceDialogViewModel.Choice<CompetencyId>> competencyBox;
    @FXML
    private TextArea noteField;
    @FXML
    private Label errorLabel;

    public EvidenceDialogController(EvidenceDialogViewModel viewModel, Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        kindBox.setItems(viewModel.kinds());
        kindBox.valueProperty().bindBidirectional(viewModel.kindProperty());
        titleField.textProperty().bindBidirectional(viewModel.titleProperty());
        datePicker.setConverter(dateConverter);
        datePicker.valueProperty().bindBidirectional(viewModel.takenOnProperty());
        // Evidence records something that already happened: later days cannot be picked.
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(!empty && item.isAfter(viewModel.today()));
            }
        });
        stepBox.setItems(viewModel.steps());
        stepBox.valueProperty().bindBidirectional(viewModel.stepProperty());
        urlField.textProperty().bindBidirectional(viewModel.urlProperty());
        competencyBox.setItems(viewModel.competencies());
        competencyBox.valueProperty().bindBidirectional(viewModel.competencyProperty());
        noteField.textProperty().bindBidirectional(viewModel.noteProperty());

        fileLabel.textProperty().bind(javafx.beans.binding.Bindings.when(viewModel.fileNameProperty().isEmpty())
                .then(messages.get("evidence.file.none")).otherwise(viewModel.fileNameProperty()));
        removeFileButton.visibleProperty().bind(viewModel.fileNameProperty().isNotEmpty());
        removeFileButton.managedProperty().bind(removeFileButton.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    @FXML
    private void chooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("evidence.file.chooseTitle"));
        File file = chooser.showOpenDialog(titleField.getScene().getWindow());
        if (file != null) {
            viewModel.chooseFile(file.toPath());
        }
    }

    @FXML
    private void removeFile() {
        viewModel.removeChosenFile();
    }

    public void create(GoalId goalId, StepId stepId) {
        viewModel.create(goalId, stepId);
    }

    public void edit(EvidenceView evidence) {
        viewModel.edit(evidence);
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

    /** A typed date only reaches the picker's value on Enter or focus loss, so read the text itself. */
    @Override
    public EvidenceView submit() {
        try {
            viewModel.takenOnProperty().set(dateConverter.fromString(datePicker.getEditor().getText()));
        } catch (DateTimeParseException notADate) {
            String example = Formats.date(LocalDate.of(2026, 10, 12));
            throw new DomainValidationException("invalid-date", "Date must be a date such as " + example + ".",
                    "Date", example);
        }
        if (viewModel.takenOnProperty().get() == null) {
            throw new DomainValidationException("required", "Date is required.", "Date");
        }
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
