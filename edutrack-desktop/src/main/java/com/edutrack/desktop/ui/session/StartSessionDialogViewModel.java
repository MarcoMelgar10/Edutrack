package com.edutrack.desktop.ui.session;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.goal.GoalService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** State of the dialog that asks what to study before the timer starts: a goal, optionally a step, free or pomodoro. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StartSessionDialogViewModel {

    /** What the learner picked. */
    public record Choice(GoalId goalId, StepId stepId, SessionTimer.Mode mode) {
    }

    private final GoalService goalService;
    private final Messages messages;

    private final ObservableList<GoalChoice> goals = FXCollections.observableArrayList();
    private final ObservableList<StepChoice> steps = FXCollections.observableArrayList();
    private final ObjectProperty<GoalChoice> goal = new SimpleObjectProperty<>();
    private final ObjectProperty<StepChoice> step = new SimpleObjectProperty<>();
    private final BooleanProperty pomodoro = new SimpleBooleanProperty(false);
    private final BooleanBinding valid;

    public StartSessionDialogViewModel(AreaService areaService, GoalService goalService, Messages messages) {
        this.goalService = goalService;
        this.messages = messages;
        goals.setAll(SessionChoices.goals(areaService, goalService));
        goal.subscribe(chosen -> {
            steps.setAll(SessionChoices.steps(goalService, chosen == null ? null : chosen.id(), messages));
            step.set(steps.getFirst());
        });
        valid = Bindings.createBooleanBinding(() -> goal.get() != null, goal);
    }

    /** Preselects a goal (and a step of it) when the dialog is opened from somewhere that knows them. */
    public void preselect(GoalId goalId, StepId stepId) {
        goals.stream().filter(choice -> choice.id().equals(goalId)).findFirst().ifPresent(goal::set);
        steps.stream().filter(choice -> choice.id() != null && choice.id().equals(stepId)).findFirst()
                .ifPresent(step::set);
    }

    public String title() {
        return messages.get("dialog.startSession.title");
    }

    public String saveLabel() {
        return messages.get("dialog.startSession.start");
    }

    public Choice submit() {
        return new Choice(goal.get().id(), step.get() == null ? null : step.get().id(),
                pomodoro.get() ? SessionTimer.Mode.POMODORO : SessionTimer.Mode.FREE);
    }

    public ObservableList<GoalChoice> goals() {
        return goals;
    }

    public ObservableList<StepChoice> steps() {
        return steps;
    }

    public ObjectProperty<GoalChoice> goalProperty() {
        return goal;
    }

    public ObjectProperty<StepChoice> stepProperty() {
        return step;
    }

    /** Whether to time in focus blocks and breaks instead of freely. */
    public BooleanProperty pomodoroProperty() {
        return pomodoro;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
