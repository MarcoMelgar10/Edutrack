package com.edutrack.application.search;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.util.UUID;

/**
 * One match of a search. {@code id} is the id of the thing found (a goal, a note's record, a card...);
 * {@code areaId}, {@code goalId} and {@code stepId} say where it lives so the UI can open it, and
 * are {@code null} where they do not apply (a journal entry about nothing in particular has none).
 * {@code snippet} is a short excerpt with the matched words wrapped in {@code «…»}; {@code rank}
 * is higher for better matches.
 */
public record SearchHit(SearchKind kind, UUID id, AreaId areaId, GoalId goalId, StepId stepId, String title,
                        String snippet, double rank) {
}
