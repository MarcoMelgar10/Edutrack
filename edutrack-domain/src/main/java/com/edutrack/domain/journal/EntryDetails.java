package com.edutrack.domain.journal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.time.LocalDate;

/**
 * The editable content of a journal entry, as entered by the user. Not validated here;
 * {@link JournalEntry} validates and normalizes it when it is applied. {@code confidence} (1 to 5)
 * only applies to check-ins.
 */
public record EntryDetails(LocalDate writtenOn, String title, String body, AreaId areaId, GoalId goalId,
                           StepId stepId, Integer confidence) {
}
