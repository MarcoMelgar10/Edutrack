package com.edutrack.application.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;

import java.time.LocalDate;

/** What a goal card in an area tab shows. */
public record GoalSummary(GoalId id, AreaId areaId, String title, LocalDate deadline, Progress progress,
                          int stepCount, int doneCount, boolean completed) {

    static GoalSummary of(Goal goal) {
        int done = (int) goal.steps().stream().filter(step -> step.status() == StepStatus.DONE).count();
        return new GoalSummary(goal.id(), goal.areaId(), goal.title(), goal.deadline(), goal.progress(),
                goal.steps().size(), done, goal.isCompleted());
    }
}
