package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;
import com.edutrack.domain.session.StudySessionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaStudySessionRepositoryAdapter implements StudySessionRepository {

    private final SpringDataStudySessionRepository repository;

    JpaStudySessionRepositoryAdapter(SpringDataStudySessionRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public StudySession save(StudySession session) {
        StudySessionJpaEntity entity = repository.findById(session.id().value())
                .orElseGet(() -> new StudySessionJpaEntity(session.id().value()));
        StudySessionMapper.copyToEntity(session, entity);
        return StudySessionMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StudySession> findById(StudySessionId id) {
        return repository.findById(id.value()).map(StudySessionMapper::toDomain);
    }

    /** Deleting a missing session does nothing. */
    @Override
    @Transactional
    public void delete(StudySessionId id) {
        repository.deleteById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudySession> findByGoal(GoalId goalId) {
        return repository.findByGoalIdOrderByStartedAtDescCreatedAtDesc(goalId.value()).stream()
                .map(StudySessionMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudySession> findByStep(StepId stepId) {
        return repository.findByStepIdOrderByStartedAtDescCreatedAtDesc(stepId.value()).stream()
                .map(StudySessionMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudySession> findStartedBetween(Instant fromInclusive, Instant toExclusive) {
        return repository.findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAscCreatedAtAsc(
                        fromInclusive, toExclusive).stream()
                .map(StudySessionMapper::toDomain)
                .toList();
    }
}
