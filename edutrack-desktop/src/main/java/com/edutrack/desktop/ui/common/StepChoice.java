package com.edutrack.desktop.ui.common;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.StepView;
import com.edutrack.domain.goal.StepId;

import java.util.ArrayList;
import java.util.List;

/**
 * An entry of a "Step" combo box in a dialog: one roadmap step of the goal, or "no step" ({@code id}
 * {@code null}). Reads "2. Listening practice", like the step page's title.
 */
public record StepChoice(StepId id, String label) {

    /** "No step" first, then the goal's steps in roadmap order. */
    public static List<StepChoice> of(GoalDetails goal, String noStepLabel) {
        List<StepChoice> choices = new ArrayList<>();
        choices.add(new StepChoice(null, noStepLabel));
        for (StepView step : goal.steps()) {
            choices.add(new StepChoice(step.id(), step.number() + ". " + step.title()));
        }
        return choices;
    }

    /** The choice for {@code stepId}, or the first ("no step") when it is {@code null} or unknown. */
    public static StepChoice find(List<StepChoice> choices, StepId stepId) {
        return choices.stream()
                .filter(choice -> stepId != null && stepId.equals(choice.id()))
                .findFirst()
                .orElse(choices.getFirst());
    }

    @Override
    public String toString() {
        return label;
    }
}
