package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.domain.area.AreaId;
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

/** Binds {@code GoalDialogView.fxml} to {@link GoalDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GoalDialogController implements DialogForm<GoalDetails> {

    private final GoalDialogViewModel viewModel;
    private final DeadlineConverter deadlineConverter = new DeadlineConverter();

    @FXML
    private TextField titleField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private DatePicker deadlinePicker;
    @FXML
    private TextField weeklyField;
    @FXML
    private Label weeklyHint;
    @FXML
    private Label errorLabel;

    public GoalDialogController(GoalDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        titleField.textProperty().bindBidirectional(viewModel.goalTitleProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionProperty());
        deadlinePicker.setConverter(deadlineConverter);
        deadlinePicker.valueProperty().bindBidirectional(viewModel.deadlineProperty());
        weeklyField.textProperty().bindBidirectional(viewModel.weeklyHoursProperty());
        weeklyHint.visibleProperty().bind(viewModel.weeklyValidProperty().not());
        weeklyHint.managedProperty().bind(weeklyHint.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    public void create(AreaId areaId) {
        viewModel.create(areaId);
    }

    public void edit(GoalDetails goal) {
        viewModel.edit(goal);
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
            viewModel.deadlineProperty().set(deadlineConverter.fromString(deadlinePicker.getEditor().getText()));
        } catch (DateTimeParseException notADate) {
            String example = Formats.date(LocalDate.of(2027, 3, 1));
            throw new DomainValidationException("invalid-date",
                    "Deadline must be a date such as " + example + ".", "Deadline", example);
        }
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
