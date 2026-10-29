package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;

final class StudySessionMapper {

    private StudySessionMapper() {
    }

    static StudySession toDomain(StudySessionJpaEntity entity) {
        return StudySession.restore(
                new StudySessionId(entity.getId()),
                new GoalId(entity.getGoalId()),
                entity.getStepId() == null ? null : new StepId(entity.getStepId()),
                entity.getRoutineId() == null ? null : new PracticeRoutineId(entity.getRoutineId()),
                entity.getStartedAt(),
                entity.getMinutes(),
                entity.getNote(),
                entity.getSource(),
                entity.getCreatedAt());
    }

    /** Copies every field except the id and the version, which belong to the persistence layer. */
    static void copyToEntity(StudySession session, StudySessionJpaEntity entity) {
        entity.setGoalId(session.goalId().value());
        entity.setStepId(session.stepId() == null ? null : session.stepId().value());
        entity.setRoutineId(session.routineId() == null ? null : session.routineId().value());
        entity.setStartedAt(session.startedAt());
        entity.setMinutes(session.minutes());
        entity.setNote(session.note());
        entity.setSource(session.source());
        entity.setCreatedAt(session.createdAt());
    }
}
