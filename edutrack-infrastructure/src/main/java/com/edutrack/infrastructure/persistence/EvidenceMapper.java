package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.evidence.Evidence;
import com.edutrack.domain.evidence.EvidenceId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

final class EvidenceMapper {

    private EvidenceMapper() {
    }

    static Evidence toDomain(EvidenceJpaEntity entity) {
        return Evidence.restore(new EvidenceId(entity.getId()), new GoalId(entity.getGoalId()),
                entity.getStepId() == null ? null : new StepId(entity.getStepId()),
                entity.getKind(), entity.getTitle(), entity.getTakenOn(), entity.getUrl(),
                entity.getAttachmentId() == null ? null : new AttachmentId(entity.getAttachmentId()),
                entity.getCompetencyId() == null ? null : new CompetencyId(entity.getCompetencyId()),
                entity.getNote(), entity.getCreatedAt());
    }

    static void copyToEntity(Evidence evidence, EvidenceJpaEntity entity) {
        entity.setGoalId(evidence.goalId().value());
        entity.setStepId(evidence.stepId() == null ? null : evidence.stepId().value());
        entity.setKind(evidence.kind());
        entity.setTitle(evidence.title());
        entity.setTakenOn(evidence.takenOn());
        entity.setUrl(evidence.url());
        entity.setAttachmentId(evidence.attachmentId() == null ? null : evidence.attachmentId().value());
        entity.setCompetencyId(evidence.competencyId() == null ? null : evidence.competencyId().value());
        entity.setNote(evidence.note());
        entity.setCreatedAt(evidence.createdAt());
    }
}
