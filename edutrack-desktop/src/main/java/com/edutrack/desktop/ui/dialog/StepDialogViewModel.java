package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * State of the Step dialog. Call {@link #create(GoalId)} for a new step at the end of a roadmap, or
 * {@link #edit(GoalId, StepView)} to change an existing one. Estimated hours are typed as text:
 * blank means "not estimated", "," and "." both work as the decimal separator.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StepDialogViewModel {

    /** English text of the rule; the UI shows the translation of {@code error.invalid-hours}. */
    static final String INVALID_HOURS = "Enter hours as a number, e.g. 2.5";

    private static final Pattern HOURS = Pattern.compile("\\d+[.,]?\\d*|[.,]\\d+");

    private final GoalService goalService;
    private final Messages messages;

    private final StringProperty stepTitle = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final StringProperty hours = new SimpleStringProperty("");
    private final ObjectProperty<StepStatus> status = new SimpleObjectProperty<>(StepStatus.NOT_STARTED);
    private final ObjectProperty<java.time.LocalDate> plannedFor = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding hoursValid;
    private final BooleanBinding valid;
    private GoalId goalId;
    private StepId editing;

    public StepDialogViewModel(GoalService goalService, Messages messages) {
        this.goalService = goalService;
        this.messages = messages;
        this.hoursValid = Bindings.createBooleanBinding(() -> {
            try {
                parseHours(hours.get());
                return true;
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }, hours);
        this.valid = Bindings.createBooleanBinding(
                () -> stepTitle.get() != null && !stepTitle.get().isBlank() && hoursValid.get(), stepTitle, hoursValid);
    }

    public void create(GoalId goalId) {
        this.goalId = goalId;
        this.editing = null;
    }

    public void edit(GoalId goalId, StepView step) {
        this.goalId = goalId;
        editing = step.id();
        stepTitle.set(step.title());
        description.set(step.description() == null ? "" : step.description());
        hours.set(step.estimatedHours() == null ? "" : Formats.number(step.estimatedHours()));
        status.set(step.status());
        plannedFor.set(step.plannedFor());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.step.new" : "dialog.step.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.step.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public GoalDetails submit() {
        error.set("");
        BigDecimal estimatedHours;
        try {
            estimatedHours = parseHours(hours.get());
        } catch (NumberFormatException notANumber) {
            throw new DomainValidationException("invalid-hours", INVALID_HOURS);
        }
        return editing == null
                ? goalService.addStep(new GoalCommands.AddStep(goalId, stepTitle.get(), description.get(),
                estimatedHours, status.get(), plannedFor.get()))
                : goalService.updateStep(new GoalCommands.UpdateStep(goalId, editing, stepTitle.get(), description.get(),
                estimatedHours, status.get(), plannedFor.get()));
    }

    /** The day the learner plans to work on the step, or {@code null}; past days are allowed. */
    public javafx.beans.property.ObjectProperty<java.time.LocalDate> plannedForProperty() {
        return plannedFor;
    }

    /** Blank text means "not estimated" ({@code null}); anything but a non-negative number throws. */
    private static BigDecimal parseHours(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String typed = text.strip();
        if (!HOURS.matcher(typed).matches()) {
            throw new NumberFormatException("Not a number of hours: " + typed);
        }
        return new BigDecimal(typed.replace(',', '.'));
    }

    public StringProperty stepTitleProperty() {
        return stepTitle;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    /** The text of the Estimated hours field. */
    public StringProperty hoursProperty() {
        return hours;
    }

    public ObjectProperty<StepStatus> statusProperty() {
        return status;
    }

    public StringProperty errorProperty() {
        return error;
    }

    /** False while the hours text is not a number; the view shows the {@code error.invalid-hours} hint. */
    public BooleanBinding hoursValidProperty() {
        return hoursValid;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
