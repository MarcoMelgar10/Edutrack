package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;

final class AttachmentMapper {

    private AttachmentMapper() {
    }

    static Attachment toDomain(AttachmentJpaEntity entity) {
        RecordOwner owner = entity.getStepId() != null
                ? RecordOwner.ofStep(new StepId(entity.getStepId()))
                : RecordOwner.ofGoal(new GoalId(entity.getGoalId()));
        return Attachment.restore(new AttachmentId(entity.getId()), owner, entity.getOriginalName(),
                entity.getStoredPath(), entity.getMimeType(), entity.getSizeBytes(), entity.getAddedAt());
    }

    static void copyToEntity(Attachment attachment, AttachmentJpaEntity entity) {
        entity.setGoalId(attachment.owner().isStep() ? null : attachment.owner().id());
        entity.setStepId(attachment.owner().isStep() ? attachment.owner().id() : null);
        entity.setOriginalName(attachment.originalName());
        entity.setStoredPath(attachment.storedPath());
        entity.setMimeType(attachment.mimeType());
        entity.setSizeBytes(attachment.sizeBytes());
        entity.setAddedAt(attachment.addedAt());
    }
}
