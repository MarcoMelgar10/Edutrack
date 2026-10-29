package com.edutrack.application.review;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;

/** What a review session covers: one goal, every goal of an area, or (both {@code null}) everything. */
public record ReviewScope(AreaId areaId, GoalId goalId) {

    public static final ReviewScope ALL = new ReviewScope(null, null);

    public static ReviewScope ofArea(AreaId areaId) {
        return new ReviewScope(areaId, null);
    }

    public static ReviewScope ofGoal(GoalId goalId) {
        return new ReviewScope(null, goalId);
    }
}
