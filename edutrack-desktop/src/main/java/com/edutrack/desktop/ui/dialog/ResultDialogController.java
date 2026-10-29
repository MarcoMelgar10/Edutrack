package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Binds {@code ResultDialogView.fxml} to {@link ResultDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ResultDialogController implements DialogForm<RecordView> {

    private final ResultDialogViewModel viewModel;
    private final Messages messages;
    private final DeadlineConverter dateConverter = new DeadlineConverter();

    @FXML
    private TextField titleField;
    @FXML
    private TextField scoreField;
    @FXML
    private TextField maxScoreField;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextArea noteField;
    @FXML
    private javafx.scene.layout.HBox measureGroup;
    @FXML
    private javafx.scene.control.ComboBox<ResultDialogViewModel.MeasureChoice> measureBox;
    @FXML
    private TextField partField;
    @FXML
    private javafx.scene.layout.VBox partGroup;
    @FXML
    private Label scoreHint;
    @FXML
    private Label errorLabel;

    public ResultDialogController(ResultDialogViewModel viewModel, Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        titleField.textProperty().bindBidirectional(viewModel.resultTitleProperty());
        scoreField.textProperty().bindBidirectional(viewModel.scoreProperty());
        maxScoreField.textProperty().bindBidirectional(viewModel.maxScoreProperty());
        noteField.textProperty().bindBidirectional(viewModel.noteProperty());
        datePicker.setConverter(dateConverter);
        datePicker.valueProperty().bindBidirectional(viewModel.takenOnProperty());

        measureBox.setItems(viewModel.measures());
        measureBox.valueProperty().bindBidirectional(viewModel.measureProperty());
        partField.textProperty().bindBidirectional(viewModel.partProperty());
        partGroup.visibleProperty().bind(viewModel.partEnabledProperty());
        partGroup.managedProperty().bind(partGroup.visibleProperty());

        scoreHint.setText(messages.get("error.invalid-score"));
        // Only complain about what was typed: an untouched empty score just keeps Save disabled.
        scoreHint.visibleProperty().bind(viewModel.scoreValidProperty().not()
                .and(viewModel.scoreProperty().isNotEmpty().or(viewModel.maxScoreProperty().isNotEmpty())));
        scoreHint.managedProperty().bind(scoreHint.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    public void create(RecordOwner owner) {
        viewModel.create(owner);
        showMeasures();
    }

    /** A new result of the goal (or of one of its steps): the goal's measures can be chosen. */
    public void create(RecordOwner owner, com.edutrack.domain.goal.GoalId goalId) {
        viewModel.create(owner, goalId);
        showMeasures();
    }

    public void edit(RecordOwner owner, RecordView.ResultView result) {
        viewModel.edit(owner, result);
        showMeasures();
    }

    public void edit(RecordOwner owner, com.edutrack.domain.goal.GoalId goalId, RecordView.ResultView result) {
        viewModel.edit(owner, goalId, result);
        showMeasures();
    }

    private void showMeasures() {
        measureGroup.setVisible(viewModel.hasMeasures());
        measureGroup.setManaged(viewModel.hasMeasures());
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
    public RecordView submit() {
        try {
            viewModel.takenOnProperty().set(dateConverter.fromString(datePicker.getEditor().getText()));
        } catch (DateTimeParseException notADate) {
            throw invalidDate();
        }
        return viewModel.submit();
    }

    private static DomainValidationException invalidDate() {
        String example = Formats.date(LocalDate.of(2026, 10, 12));
        return new DomainValidationException("invalid-date", "Date must be a date such as " + example + ".",
                "Date", example);
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
