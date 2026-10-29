package com.edutrack.desktop.ui.practice;

import com.edutrack.application.goal.GoalService;
import com.edutrack.application.practice.PracticeCommands;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.SessionChoices;
import com.edutrack.desktop.ui.session.StepChoice;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutine;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * State of the practice-routine dialog: a title, an optional step, how often (every day or some days a
 * week) and the minutes each time. "Every day" fixes the times per week at 7.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RoutineDialogViewModel {

    /** An entry of the cadence box. */
    public record CadenceChoice(Cadence cadence, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final PracticeService practiceService;
    private final GoalService goalService;
    private final Messages messages;

    private final ObservableList<StepChoice> steps = FXCollections.observableArrayList();
    private final ObservableList<CadenceChoice> cadences;
    private final StringProperty title = new SimpleStringProperty("");
    private final ObjectProperty<StepChoice> step = new SimpleObjectProperty<>();
    private final ObjectProperty<CadenceChoice> cadence = new SimpleObjectProperty<>();
    private final StringProperty timesPerWeek = new SimpleStringProperty("3");
    private final StringProperty minutes = new SimpleStringProperty("15");
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding timesVisible;
    private final BooleanBinding valid;

    private GoalId goalId;
    private RoutineView editing;

    public RoutineDialogViewModel(PracticeService practiceService, GoalService goalService, Messages messages) {
        this.practiceService = practiceService;
        this.goalService = goalService;
        this.messages = messages;
        this.cadences = FXCollections.observableArrayList(
                new CadenceChoice(Cadence.DAILY, messages.get("routine.cadence.daily")),
                new CadenceChoice(Cadence.WEEKLY, messages.get("routine.cadence.weekly")));
        cadence.set(cadences.getFirst());
        this.timesVisible = Bindings.createBooleanBinding(
                () -> cadence.get() != null && cadence.get().cadence() == Cadence.WEEKLY, cadence);
        this.valid = Bindings.createBooleanBinding(() -> !title.get().isBlank()
                        && parse(minutes.get(), 1, PracticeRoutine.MAX_TARGET_MINUTES) != null
                        && (!timesVisible.get() || parse(timesPerWeek.get(), 1, 7) != null),
                title, minutes, timesPerWeek, cadence);
    }

    public void create(GoalId goal) {
        goalId = goal;
        editing = null;
        loadSteps(null);
    }

    public void edit(RoutineView routine) {
        goalId = routine.goalId();
        editing = routine;
        loadSteps(routine.stepId());
        title.set(routine.title());
        cadence.set(cadences.stream().filter(choice -> choice.cadence() == routine.cadence()).findFirst().orElseThrow());
        timesPerWeek.set(String.valueOf(routine.timesPerWeek()));
        minutes.set(String.valueOf(routine.targetMinutes()));
    }

    public String title() {
        return messages.get(editing == null ? "dialog.routine.new" : "dialog.routine.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.routine.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public RoutineView submit() {
        error.set("");
        Cadence chosen = cadence.get().cadence();
        Integer times = chosen == Cadence.WEEKLY ? parse(timesPerWeek.get(), 1, 7) : Integer.valueOf(7);
        Integer target = parse(minutes.get(), 1, PracticeRoutine.MAX_TARGET_MINUTES);
        var stepId = step.get() == null ? null : step.get().id();
        if (editing != null) {
            return practiceService.update(new PracticeCommands.UpdateRoutine(editing.id(), stepId, title.get(), chosen,
                    times, target));
        }
        return practiceService.create(new PracticeCommands.CreateRoutine(goalId, stepId, title.get(), chosen, times,
                target));
    }

    private void loadSteps(com.edutrack.domain.goal.StepId selected) {
        steps.setAll(SessionChoices.steps(goalService, goalId, messages));
        step.set(steps.stream().filter(choice -> Objects.equals(choice.id(), selected)).findFirst()
                .orElse(steps.getFirst()));
    }

    /** The whole number in {@code [min, max]}, or {@code null}. */
    static Integer parse(String text, int min, int max) {
        if (text == null || !text.strip().matches("\\d{1,4}")) {
            return null;
        }
        int value = Integer.parseInt(text.strip());
        return value >= min && value <= max ? value : null;
    }

    public ObservableList<StepChoice> steps() {
        return steps;
    }

    public ObservableList<CadenceChoice> cadences() {
        return cadences;
    }

    public StringProperty titleProperty() {
        return title;
    }

    public ObjectProperty<StepChoice> stepProperty() {
        return step;
    }

    public ObjectProperty<CadenceChoice> cadenceProperty() {
        return cadence;
    }

    public StringProperty timesPerWeekProperty() {
        return timesPerWeek;
    }

    public StringProperty minutesProperty() {
        return minutes;
    }

    public StringProperty errorProperty() {
        return error;
    }

    /** True while "times per week" applies (the weekly cadence). */
    public BooleanBinding timesVisibleProperty() {
        return timesVisible;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
