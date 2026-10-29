package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;

import java.time.Instant;
import java.util.UUID;

/** Saves the area and goal a test needs, so rows with foreign keys to a goal or step can be inserted. */
final class GoalFixtures {

    static final Instant NOW = Instant.parse("2026-03-01T09:30:15.123456Z");

    private GoalFixtures() {
    }

    /** A goal in a new area, with {@code steps} roadmap steps titled "Step 1", "Step 2", ... */
    static Goal saveGoalWithSteps(AreaRepository areas, GoalRepository goals, int steps) {
        Area area = areas.save(Area.create("Area " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW));
        Goal goal = Goal.create(area.id(), "Reach B2", null, null, 0, NOW);
        for (int i = 1; i <= steps; i++) {
            goal.addStep(new StepDetails("Step " + i, null, null), StepStatus.NOT_STARTED, NOW);
        }
        return goals.save(goal);
    }
}
