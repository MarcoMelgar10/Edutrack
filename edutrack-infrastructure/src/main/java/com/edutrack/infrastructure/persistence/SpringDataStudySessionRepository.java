package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataStudySessionRepository extends JpaRepository<StudySessionJpaEntity, UUID> {

    List<StudySessionJpaEntity> findByGoalIdOrderByStartedAtDescCreatedAtDesc(UUID goalId);

    List<StudySessionJpaEntity> findByStepIdOrderByStartedAtDescCreatedAtDesc(UUID stepId);

    List<StudySessionJpaEntity> findByStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAscCreatedAtAsc(
            Instant fromInclusive, Instant toExclusive);
}
