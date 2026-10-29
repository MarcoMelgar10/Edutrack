package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class GoalMapper {

    private GoalMapper() {
    }

    /** Must run inside the transaction that loaded the entity, so the step collection can be read. */
    static Goal toDomain(GoalJpaEntity entity) {
        List<RoadmapStep> steps = entity.getSteps().stream()
                .sorted(Comparator.comparingInt(RoadmapStepJpaEntity::getPosition))
                .map(GoalMapper::toDomain)
                .toList();
        return Goal.restore(
                new GoalId(entity.getId()),
                new AreaId(entity.getAreaId()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getDeadline(),
                entity.getWeeklyTargetMinutes(),
                entity.getPosition(),
                entity.getCreatedAt(),
                steps);
    }

    private static RoadmapStep toDomain(RoadmapStepJpaEntity entity) {
        return RoadmapStep.restore(
                new StepId(entity.getId()),
                entity.getTitle(),
                entity.getDescription(),
                entity.getEstimatedHours(),
                entity.getPlannedFor(),
                entity.getStatus(),
                entity.getCompletedAt());
    }

    /**
     * Brings the entity in line with the goal. The step collection is synchronized in place and
     * never replaced: steps that still exist keep their managed entity (one instance per id),
     * new steps get a new entity, steps no longer in the goal are dropped from the list (orphan
     * removal deletes them) and the list order follows the roadmap order, stored as the position.
     */
    static void copyToEntity(Goal goal, GoalJpaEntity entity) {
        entity.setAreaId(goal.areaId().value());
        entity.setTitle(goal.title());
        entity.setDescription(goal.description());
        entity.setDeadline(goal.deadline());
        entity.setWeeklyTargetMinutes(goal.weeklyTargetMinutes());
        entity.setPosition(goal.position());
        entity.setCreatedAt(goal.createdAt());

        Map<UUID, RoadmapStepJpaEntity> existing = new HashMap<>();
        for (RoadmapStepJpaEntity step : entity.getSteps()) {
            existing.put(step.getId(), step);
        }

        List<RoadmapStepJpaEntity> ordered = new ArrayList<>(goal.steps().size());
        int position = 0;
        for (RoadmapStep step : goal.steps()) {
            UUID stepId = step.id().value();
            RoadmapStepJpaEntity stepEntity = existing.get(stepId);
            if (stepEntity == null) {
                stepEntity = new RoadmapStepJpaEntity(stepId);
                stepEntity.setGoal(entity);
            }
            copyToEntity(step, position++, stepEntity);
            ordered.add(stepEntity);
        }

        List<RoadmapStepJpaEntity> managed = entity.getSteps();
        managed.clear();
        managed.addAll(ordered);
    }

    private static void copyToEntity(RoadmapStep step, int position, RoadmapStepJpaEntity entity) {
        entity.setPosition(position);
        entity.setTitle(step.title());
        entity.setDescription(step.description());
        entity.setEstimatedHours(step.estimatedHours());
        entity.setPlannedFor(step.plannedFor());
        entity.setStatus(step.status());
        entity.setCompletedAt(step.completedAt());
    }
}
