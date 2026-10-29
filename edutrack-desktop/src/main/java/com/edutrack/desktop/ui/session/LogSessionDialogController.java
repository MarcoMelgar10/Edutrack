package com.edutrack.desktop.ui.session;

import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.DeadlineConverter;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DateCell;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** Binds {@code LogSessionDialogView.fxml} to {@link LogSessionDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LogSessionDialogController implements DialogForm<SessionView> {

    private final LogSessionDialogViewModel viewModel;
    private final Messages messages;
    private final DeadlineConverter dateConverter = new DeadlineConverter();

    @FXML
    private ComboBox<GoalChoice> goalBox;
    @FXML
    private ComboBox<StepChoice> stepBox;
    @FXML
    private DatePicker datePicker;
    @FXML
    private TextField timeField;
    @FXML
    private TextField minutesField;
    @FXML
    private TextArea noteField;
    @FXML
    private Label hintLabel;
    @FXML
    private Label errorLabel;

    public LogSessionDialogController(LogSessionDialogViewModel viewModel, Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        goalBox.setItems(viewModel.goals());
        goalBox.valueProperty().bindBidirectional(viewModel.goalProperty());
        goalBox.disableProperty().bind(viewModel.goalLockedProperty());
        stepBox.setItems(viewModel.steps());
        stepBox.valueProperty().bindBidirectional(viewModel.stepProperty());

        datePicker.setConverter(dateConverter);
        datePicker.valueProperty().bindBidirectional(viewModel.dateProperty());
        // A session records something that already happened: later days cannot be picked.
        datePicker.setDayCellFactory(picker -> new DateCell() {
            @Override
            public void updateItem(LocalDate item, boolean empty) {
                super.updateItem(item, empty);
                setDisable(!empty && item.isAfter(viewModel.today()));
            }
        });

        timeField.textProperty().bindBidirectional(viewModel.timeProperty());
        minutesField.textProperty().bindBidirectional(viewModel.minutesProperty());
        noteField.textProperty().bindBidirectional(viewModel.noteProperty());

        hintLabel.setText(messages.get("session.minutes.hint"));
        // Only complain about what was typed: an untouched empty field just keeps Save disabled.
        hintLabel.visibleProperty().bind(viewModel.minutesValidProperty().not()
                .and(viewModel.minutesProperty().isNotEmpty())
                .or(viewModel.timeValidProperty().not().and(viewModel.timeProperty().isNotEmpty())));
        hintLabel.managedProperty().bind(hintLabel.visibleProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(minutesField::requestFocus);
    }

    /** A new entry; the goal and the step may be {@code null} (the learner picks them). */
    public void create(GoalId goalId, StepId stepId) {
        viewModel.create(goalId, stepId);
    }

    /** A new entry for a session the timer could not save. */
    public void prefill(GoalId goalId, StepId stepId, PracticeRoutineId routineId, Instant startedAt, int minutes,
                        SessionSource source) {
        viewModel.prefill(goalId, stepId, routineId, startedAt, minutes, source);
    }

    public void edit(SessionView session) {
        viewModel.edit(session);
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
    public SessionView submit() {
        try {
            viewModel.dateProperty().set(dateConverter.fromString(datePicker.getEditor().getText()));
        } catch (DateTimeParseException notADate) {
            String example = Formats.date(LocalDate.of(2026, 10, 12));
            throw new DomainValidationException("invalid-date", "Date must be a date such as " + example + ".",
                    "Date", example);
        }
        if (viewModel.dateProperty().get() == null) {
            throw new DomainValidationException("required", "Date is required.", "Date");
        }
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
