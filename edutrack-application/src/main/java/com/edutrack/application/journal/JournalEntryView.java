package com.edutrack.application.journal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.journal.JournalEntry;
import com.edutrack.domain.journal.JournalEntryId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * One journal entry as the journal shows it, with the titles of the goal and step it is linked to.
 * {@code confidence} and {@code weekMinutes} only exist on check-ins; the titles are {@code null}
 * when there is no link or the target no longer exists.
 */
public record JournalEntryView(JournalEntryId id, EntryKind kind, LocalDate writtenOn, String title, String body,
                               AreaId areaId, GoalId goalId, String goalTitle, StepId stepId, String stepTitle,
                               Integer confidence, Integer weekMinutes, Instant createdAt, Instant updatedAt) {

    static JournalEntryView of(JournalEntry entry, Map<GoalId, String> goalTitles, Map<StepId, String> stepTitles) {
        return new JournalEntryView(entry.id(), entry.kind(), entry.writtenOn(), entry.title(), entry.body(),
                entry.areaId(), entry.goalId(), entry.goalId() == null ? null : goalTitles.get(entry.goalId()),
                entry.stepId(), entry.stepId() == null ? null : stepTitles.get(entry.stepId()),
                entry.confidence(), entry.weekMinutes(), entry.createdAt(), entry.updatedAt());
    }
}
