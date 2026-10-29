package com.edutrack.domain.session;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;

import java.time.Instant;

/**
 * The editable content of a study session, as entered by the user. Not validated here;
 * {@link StudySession} validates and normalizes it when it is applied.
 */
public record SessionDetails(GoalId goalId, StepId stepId, PracticeRoutineId routineId, Instant startedAt,
                             Integer minutes, String note) {
}
