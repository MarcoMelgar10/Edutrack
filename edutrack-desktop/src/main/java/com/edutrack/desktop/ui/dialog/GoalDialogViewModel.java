package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.goal.GoalService;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * State of the Goal dialog. Call {@link #create(AreaId)} for a new goal in an area, or
 * {@link #edit(GoalDetails)} to change an existing one.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GoalDialogViewModel {

    private final GoalService goalService;
    private final Messages messages;

    private final StringProperty goalTitle = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> deadline = new SimpleObjectProperty<>();
    private final StringProperty weeklyHours = new SimpleStringProperty("");
    private final BooleanBinding weeklyValid;
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private AreaId areaId;
    private GoalId editing;

    public GoalDialogViewModel(GoalService goalService, Messages messages) {
        this.goalService = goalService;
        this.messages = messages;
        this.weeklyValid = Bindings.createBooleanBinding(() -> isWeeklyHoursValid(weeklyHours.get()), weeklyHours);
        this.valid = Bindings.createBooleanBinding(
                () -> goalTitle.get() != null && !goalTitle.get().isBlank() && weeklyValid.get(), goalTitle, weeklyValid);
    }

    public void create(AreaId areaId) {
        this.areaId = areaId;
        this.editing = null;
    }

    public void edit(GoalDetails goal) {
        editing = goal.id();
        areaId = goal.areaId();
        goalTitle.set(goal.title());
        description.set(goal.description() == null ? "" : goal.description());
        deadline.set(goal.deadline());
        weeklyHours.set(goal.weeklyTargetMinutes() == null ? ""
                : java.math.BigDecimal.valueOf(goal.weeklyTargetMinutes()).divide(java.math.BigDecimal.valueOf(60), 2,
                java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.goal.new" : "dialog.goal.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.goal.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public GoalDetails submit() {
        error.set("");
        return editing == null
                ? goalService.createGoal(new GoalCommands.CreateGoal(areaId, goalTitle.get(), description.get(),
                deadline.get(), weeklyMinutes()))
                : goalService.updateGoal(new GoalCommands.UpdateGoal(editing, goalTitle.get(), description.get(),
                deadline.get(), weeklyMinutes()));
    }

    /** The typed hours a week as whole minutes; {@code null} when the field is blank. */
    Integer weeklyMinutes() {
        String text = weeklyHours.get();
        if (text == null || text.isBlank()) {
            return null;
        }
        return java.math.BigDecimal.valueOf(Double.parseDouble(text.strip().replace(',', '.')))
                .multiply(java.math.BigDecimal.valueOf(60)).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
    }

    /** Blank (no target) or a positive number of hours such as "5", "7.5" or "7,5". */
    static boolean isWeeklyHoursValid(String text) {
        if (text == null || text.isBlank()) {
            return true;
        }
        String normalized = text.strip().replace(',', '.');
        return normalized.matches("\\d{1,3}(\\.\\d{1,2})?") && Double.parseDouble(normalized) > 0;
    }

    /** Hours of study a week the learner wants to give the goal; blank for none. */
    public StringProperty weeklyHoursProperty() {
        return weeklyHours;
    }

    public BooleanBinding weeklyValidProperty() {
        return weeklyValid;
    }

    public StringProperty goalTitleProperty() {
        return goalTitle;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    /** {@code null} when the goal has no deadline. */
    public ObjectProperty<LocalDate> deadlineProperty() {
        return deadline;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
