package com.edutrack.domain.evidence;

import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.goal.StepId;

import java.time.LocalDate;

/**
 * Evidence as typed in its dialog. Validated by {@link Evidence}. {@code stepId} and
 * {@code competencyId} are optional.
 */
public record EvidenceDetails(StepId stepId, EvidenceKind kind, String title, LocalDate takenOn, String url,
                              String note, CompetencyId competencyId) {
}
