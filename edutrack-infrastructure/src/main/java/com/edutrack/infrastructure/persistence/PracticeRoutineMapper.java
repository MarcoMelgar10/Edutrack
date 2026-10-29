package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.PracticeRoutineId;

final class PracticeRoutineMapper {

    private PracticeRoutineMapper() {
    }

    static PracticeRoutine toDomain(PracticeRoutineJpaEntity entity) {
        return PracticeRoutine.restore(
                new PracticeRoutineId(entity.getId()),
                new GoalId(entity.getGoalId()),
                entity.getStepId() == null ? null : new StepId(entity.getStepId()),
                entity.getTitle(),
                entity.getCadence(),
                entity.getTimesPerWeek(),
                entity.getTargetMinutes(),
                entity.isActive(),
                entity.getPosition(),
                entity.getCreatedAt());
    }

    /** Copies every field except the id and the version, which belong to the persistence layer. */
    static void copyToEntity(PracticeRoutine routine, PracticeRoutineJpaEntity entity) {
        entity.setGoalId(routine.goalId().value());
        entity.setStepId(routine.stepId() == null ? null : routine.stepId().value());
        entity.setTitle(routine.title());
        entity.setCadence(routine.cadence());
        entity.setTimesPerWeek(routine.timesPerWeek());
        entity.setTargetMinutes(routine.targetMinutes());
        entity.setActive(routine.active());
        entity.setPosition(routine.position());
        entity.setCreatedAt(routine.createdAt());
    }
}
