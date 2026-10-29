package com.edutrack.application.progress;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.progress.Pace;

/** A goal with how its recent study time compares with its plan. */
public record GoalPace(GoalId goalId, AreaId areaId, String title, Pace pace) {
}
