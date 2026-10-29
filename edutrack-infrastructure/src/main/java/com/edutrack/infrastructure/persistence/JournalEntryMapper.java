package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.journal.JournalEntry;
import com.edutrack.domain.journal.JournalEntryId;

final class JournalEntryMapper {

    private JournalEntryMapper() {
    }

    static JournalEntry toDomain(JournalEntryJpaEntity entity) {
        return JournalEntry.restore(
                new JournalEntryId(entity.getId()),
                entity.getKind(),
                entity.getWrittenOn(),
                entity.getTitle(),
                entity.getBody(),
                entity.getAreaId() == null ? null : new AreaId(entity.getAreaId()),
                entity.getGoalId() == null ? null : new GoalId(entity.getGoalId()),
                entity.getStepId() == null ? null : new StepId(entity.getStepId()),
                entity.getConfidence(),
                entity.getWeekMinutes(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    /** Copies every field except the id and the version, which belong to the persistence layer. */
    static void copyToEntity(JournalEntry entry, JournalEntryJpaEntity entity) {
        entity.setKind(entry.kind());
        entity.setWrittenOn(entry.writtenOn());
        entity.setTitle(entry.title());
        entity.setBody(entry.body());
        entity.setAreaId(entry.areaId() == null ? null : entry.areaId().value());
        entity.setGoalId(entry.goalId() == null ? null : entry.goalId().value());
        entity.setStepId(entry.stepId() == null ? null : entry.stepId().value());
        entity.setConfidence(entry.confidence());
        entity.setWeekMinutes(entry.weekMinutes());
        entity.setCreatedAt(entry.createdAt());
        entity.setUpdatedAt(entry.updatedAt());
    }
}
