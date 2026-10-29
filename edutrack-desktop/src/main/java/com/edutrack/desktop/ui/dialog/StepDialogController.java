package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.StepView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepStatus;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code StepDialogView.fxml} to {@link StepDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StepDialogController implements DialogForm<GoalDetails> {

    private final StepDialogViewModel viewModel;
    private final DeadlineConverter plannedConverter = new DeadlineConverter();
    private final Messages messages;

    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private TextField hoursField;
    @FXML
    private javafx.scene.control.DatePicker plannedPicker;
    @FXML
    private ComboBox<StepStatus> statusBox;
    @FXML
    private Label hoursHint;
    @FXML
    private Label errorLabel;

    public StepDialogController(StepDialogViewModel viewModel, Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        titleField.textProperty().bindBidirectional(viewModel.stepTitleProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionProperty());
        hoursField.textProperty().bindBidirectional(viewModel.hoursProperty());
        plannedPicker.setConverter(plannedConverter);
        plannedPicker.valueProperty().bindBidirectional(viewModel.plannedForProperty());

        statusBox.setItems(FXCollections.observableArrayList(StepStatus.values()));
        statusBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(StepStatus status) {
                return status == null ? "" : Formats.status(status);
            }

            @Override
            public StepStatus fromString(String text) {
                return null; // the combo box is not editable
            }
        });
        statusBox.valueProperty().bindBidirectional(viewModel.statusProperty());

        hoursHint.setText(messages.get("error.invalid-hours"));
        hoursHint.visibleProperty().bind(viewModel.hoursValidProperty().not());
        hoursHint.managedProperty().bind(hoursHint.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    public void create(GoalId goalId) {
        viewModel.create(goalId);
    }

    public void edit(GoalId goalId, StepView step) {
        viewModel.edit(goalId, step);
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
    public GoalDetails submit() {
        try {
            viewModel.plannedForProperty().set(plannedConverter.fromString(plannedPicker.getEditor().getText()));
        } catch (java.time.format.DateTimeParseException notADate) {
            String example = com.edutrack.desktop.ui.common.Formats.date(java.time.LocalDate.of(2027, 3, 1));
            throw new com.edutrack.domain.shared.DomainValidationException("invalid-date",
                    "Planned date must be a date such as " + example + ".", "Planned date", example);
        }
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
