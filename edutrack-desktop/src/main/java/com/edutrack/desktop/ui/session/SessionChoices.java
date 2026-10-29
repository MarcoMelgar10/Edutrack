package com.edutrack.desktop.ui.session;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.goal.StepView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;

import java.util.ArrayList;
import java.util.List;

/** What the goal and step boxes of the session dialogs offer. */
public final class SessionChoices {

    private SessionChoices() {
    }

    /** Every goal of every area, in area order, labelled "Area · Goal". */
    public static List<GoalChoice> goals(AreaService areaService, GoalService goalService) {
        List<GoalChoice> choices = new ArrayList<>();
        for (AreaSummary area : areaService.listAreas()) {
            for (GoalSummary goal : goalService.listGoals(area.id())) {
                choices.add(new GoalChoice(goal.id(), area.name() + " · " + goal.title()));
            }
        }
        return choices;
    }

    /** "No specific step" followed by the goal's steps; only the first entry for no goal. */
    public static List<StepChoice> steps(GoalService goalService, GoalId goalId, Messages messages) {
        List<StepChoice> choices = new ArrayList<>();
        choices.add(new StepChoice(null, messages.get("session.noStep")));
        if (goalId != null) {
            for (StepView step : goalService.getGoal(goalId).steps()) {
                choices.add(new StepChoice(step.id(), step.number() + ". " + step.title()));
            }
        }
        return choices;
    }
}
