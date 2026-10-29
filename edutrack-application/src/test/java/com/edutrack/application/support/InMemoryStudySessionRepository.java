package com.edutrack.application.support;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;
import com.edutrack.domain.session.StudySessionRepository;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryStudySessionRepository implements StudySessionRepository {

    private static final Comparator<StudySession> NEWEST_FIRST = Comparator.comparing(StudySession::startedAt).reversed();

    private final Map<StudySessionId, StudySession> sessions = new LinkedHashMap<>();

    @Override
    public StudySession save(StudySession session) {
        sessions.put(session.id(), session);
        return session;
    }

    @Override
    public Optional<StudySession> findById(StudySessionId id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public void delete(StudySessionId id) {
        sessions.remove(id);
    }

    @Override
    public List<StudySession> findByGoal(GoalId goalId) {
        return sessions.values().stream().filter(session -> session.goalId().equals(goalId)).sorted(NEWEST_FIRST).toList();
    }

    @Override
    public List<StudySession> findByStep(StepId stepId) {
        return sessions.values().stream().filter(session -> stepId.equals(session.stepId())).sorted(NEWEST_FIRST).toList();
    }

    @Override
    public List<StudySession> findStartedBetween(Instant fromInclusive, Instant toExclusive) {
        return sessions.values().stream()
                .filter(session -> !session.startedAt().isBefore(fromInclusive) && session.startedAt().isBefore(toExclusive))
                .sorted(Comparator.comparing(StudySession::startedAt))
                .toList();
    }

    public int size() {
        return sessions.size();
    }
}
