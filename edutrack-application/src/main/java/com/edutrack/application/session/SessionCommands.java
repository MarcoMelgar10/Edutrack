package com.edutrack.application.session;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySessionId;

import java.time.Instant;

/** Input for the session use cases, as typed in the log dialog or taken from the study timer. Validated by the domain. */
public final class SessionCommands {

    private SessionCommands() {
    }

    /** A missing source means the session was typed in by hand. */
    public record LogSession(GoalId goalId, StepId stepId, PracticeRoutineId routineId, Instant startedAt,
                             Integer minutes, String note, SessionSource source) {

        public LogSession {
            source = source == null ? SessionSource.MANUAL : source;
        }
    }

    /** A session keeps its goal, routine and source; the rest can be corrected. */
    public record UpdateSession(StudySessionId sessionId, StepId stepId, Instant startedAt, Integer minutes,
                                String note) {
    }
}
