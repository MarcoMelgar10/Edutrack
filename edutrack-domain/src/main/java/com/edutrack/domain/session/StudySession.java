package com.edutrack.domain.session;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root: a block of study time logged on a goal, optionally on one of its steps and on a
 * practice routine. It records something that already happened, so it cannot start in the future.
 * Sessions reference goals, steps and routines by id only.
 */
public final class StudySession {

    public static final int MIN_MINUTES = 1;
    public static final int MAX_MINUTES = 1440;
    public static final int NOTE_MAX_LENGTH = 500;

    private final StudySessionId id;
    private final SessionSource source;
    private final Instant createdAt;
    private GoalId goalId;
    private StepId stepId;
    private PracticeRoutineId routineId;
    private Instant startedAt;
    private int minutes;
    private String note;

    private StudySession(StudySessionId id, SessionDetails details, SessionSource source, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.source = Validation.required(source, "Source");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        apply(details);
    }

    public static StudySession create(SessionDetails details, SessionSource source, Instant now) {
        Objects.requireNonNull(now, "now");
        ensureNotInFuture(details, now);
        return new StudySession(StudySessionId.newId(), details, source, now);
    }

    /** Rebuilds a session from storage, without the date check (it was valid when logged). */
    public static StudySession restore(StudySessionId id, GoalId goalId, StepId stepId, PracticeRoutineId routineId,
                                       Instant startedAt, int minutes, String note, SessionSource source,
                                       Instant createdAt) {
        return new StudySession(id, new SessionDetails(goalId, stepId, routineId, startedAt, minutes, note),
                source, createdAt);
    }

    /** A session records something already done, so it cannot start after {@code now}. */
    public void update(SessionDetails details, Instant now) {
        Objects.requireNonNull(now, "now");
        ensureNotInFuture(details, now);
        apply(details);
    }

    private static void ensureNotInFuture(SessionDetails details, Instant now) {
        Objects.requireNonNull(details, "details");
        if (details.startedAt() != null && details.startedAt().isAfter(now)) {
            throw new DomainValidationException("future-date", "Start cannot be in the future.", "Start");
        }
    }

    /** All-or-nothing: every value is validated before any field changes. */
    private void apply(SessionDetails details) {
        Objects.requireNonNull(details, "details");
        GoalId validGoal = Validation.required(details.goalId(), "Goal");
        Instant validStart = Validation.required(details.startedAt(), "Start");
        Integer validMinutes = Validation.required(details.minutes(), "Minutes");
        if (validMinutes < MIN_MINUTES || validMinutes > MAX_MINUTES) {
            throw new DomainValidationException("out-of-range",
                    "Minutes must be between " + MIN_MINUTES + " and " + MAX_MINUTES + ".",
                    "Minutes", MIN_MINUTES, MAX_MINUTES);
        }
        String validNote = Validation.optionalText(details.note(), "Note", NOTE_MAX_LENGTH);
        this.goalId = validGoal;
        this.stepId = details.stepId();
        this.routineId = details.routineId();
        this.startedAt = validStart;
        this.minutes = validMinutes;
        this.note = validNote;
    }

    public StudySessionId id() {
        return id;
    }

    public GoalId goalId() {
        return goalId;
    }

    public StepId stepId() {
        return stepId;
    }

    public PracticeRoutineId routineId() {
        return routineId;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public int minutes() {
        return minutes;
    }

    public String note() {
        return note;
    }

    public SessionSource source() {
        return source;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StudySession session && id.equals(session.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "StudySession[" + minutes + " min on " + goalId + " at " + startedAt + "]";
    }
}
