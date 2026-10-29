package com.edutrack.domain.session;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port for storing study sessions. Implemented by the infrastructure layer. */
public interface StudySessionRepository {

    StudySession save(StudySession session);

    Optional<StudySession> findById(StudySessionId id);

    /** Deleting a missing session does nothing. */
    void delete(StudySessionId id);

    /** Sessions of one goal, newest first. */
    List<StudySession> findByGoal(GoalId goalId);

    /** Sessions of one step, newest first. */
    List<StudySession> findByStep(StepId stepId);

    /** Sessions of every goal that started in [{@code fromInclusive}, {@code toExclusive}), oldest first. */
    List<StudySession> findStartedBetween(Instant fromInclusive, Instant toExclusive);
}
