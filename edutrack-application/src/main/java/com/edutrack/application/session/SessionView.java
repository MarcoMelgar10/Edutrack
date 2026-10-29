package com.edutrack.application.session;

import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;

import java.time.Instant;

/** One logged session as the session list shows it; {@code stepTitle} is {@code null} without a step. */
public record SessionView(StudySessionId id, GoalId goalId, StepId stepId, String stepTitle,
                          PracticeRoutineId routineId, Instant startedAt, int minutes, String note,
                          SessionSource source) {

    /** {@code goal} is the session's goal, used to resolve the step title; it may be {@code null}. */
    public static SessionView of(StudySession session, Goal goal) {
        String stepTitle = goal == null || session.stepId() == null ? null : goal.steps().stream()
                .filter(step -> step.id().equals(session.stepId()))
                .map(RoadmapStep::title)
                .findFirst()
                .orElse(null);
        return new SessionView(session.id(), session.goalId(), session.stepId(), stepTitle, session.routineId(),
                session.startedAt(), session.minutes(), session.note(), session.source());
    }
}
