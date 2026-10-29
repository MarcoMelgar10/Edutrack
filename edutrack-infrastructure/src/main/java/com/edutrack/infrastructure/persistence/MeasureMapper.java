package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.skill.CompetencyId;

final class MeasureMapper {

    private MeasureMapper() {
    }

    static Measure toDomain(MeasureJpaEntity entity) {
        return Measure.restore(
                new MeasureId(entity.getId()),
                new GoalId(entity.getGoalId()),
                new MeasureDetails(entity.getName(), entity.getUnit(), entity.getMaxValue(), entity.getTarget(),
                        entity.getDirection(), entity.getCombineRule(),
                        entity.getCompetencyId() == null ? null : new CompetencyId(entity.getCompetencyId())),
                entity.getPosition(),
                entity.getCreatedAt());
    }

    static void copyToEntity(Measure measure, MeasureJpaEntity entity) {
        entity.setGoalId(measure.goalId().value());
        entity.setName(measure.name());
        entity.setUnit(measure.unit());
        entity.setMaxValue(measure.maxValue());
        entity.setTarget(measure.target());
        entity.setDirection(measure.direction());
        entity.setCombineRule(measure.combineRule());
        entity.setCompetencyId(measure.competencyId() == null ? null : measure.competencyId().value());
        entity.setPosition(measure.position());
        entity.setCreatedAt(measure.createdAt());
    }
}
