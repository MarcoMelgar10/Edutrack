package com.edutrack.application.evidence;

import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.application.attachment.AttachmentView;
import com.edutrack.domain.evidence.Evidence;
import com.edutrack.domain.evidence.EvidenceId;
import com.edutrack.domain.evidence.EvidenceKind;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A piece of evidence as the UI lists it. {@code stepId}/{@code stepTitle}, {@code attachment} and
 * {@code competencyId} are {@code null} when the evidence has none.
 */
public record EvidenceView(EvidenceId id, GoalId goalId, StepId stepId, String stepTitle, EvidenceKind kind,
                           String title, LocalDate takenOn, String url, String note, CompetencyId competencyId,
                           AttachmentView attachment, Instant createdAt) {

    static EvidenceView of(Evidence evidence, String stepTitle, AttachmentView attachment) {
        return new EvidenceView(evidence.id(), evidence.goalId(), evidence.stepId(), stepTitle, evidence.kind(),
                evidence.title(), evidence.takenOn(), evidence.url(), evidence.note(), evidence.competencyId(),
                attachment, evidence.createdAt());
    }
}
