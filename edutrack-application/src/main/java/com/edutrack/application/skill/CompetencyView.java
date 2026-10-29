package com.edutrack.application.skill;

import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.LevelChange;

import java.util.List;

/**
 * One competency as the skills panel shows it. {@code history} lists the dated level changes
 * (oldest first, the starting level 0 is implied); {@code steps} are the linked roadmap steps in
 * roadmap order.
 */
public record CompetencyView(CompetencyId id, GoalId goalId, String name, String description, int level,
                             int targetLevel, boolean meetsTarget, int position, List<LevelChange> history,
                             List<LinkedStep> steps) {

    public record LinkedStep(StepId id, String title) {
    }

    static CompetencyView of(Competency competency, Goal goal) {
        List<LinkedStep> linked = goal.steps().stream()
                .filter(step -> competency.steps().contains(step.id()))
                .map(CompetencyView::linked)
                .toList();
        return new CompetencyView(competency.id(), competency.goalId(), competency.name(), competency.description(),
                competency.level(), competency.targetLevel(), competency.meetsTarget(), competency.position(),
                List.copyOf(competency.history()), linked);
    }

    private static LinkedStep linked(RoadmapStep step) {
        return new LinkedStep(step.id(), step.title());
    }
}
