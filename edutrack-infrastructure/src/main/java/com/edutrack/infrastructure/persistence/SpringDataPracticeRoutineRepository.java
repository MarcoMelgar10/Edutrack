package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataPracticeRoutineRepository extends JpaRepository<PracticeRoutineJpaEntity, UUID> {

    List<PracticeRoutineJpaEntity> findByGoalIdOrderByPositionAscCreatedAtAsc(UUID goalId);

    List<PracticeRoutineJpaEntity> findByActiveTrueOrderByPositionAscCreatedAtAsc();
}
