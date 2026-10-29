package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataGoalRepository extends JpaRepository<GoalJpaEntity, UUID> {

    @EntityGraph(attributePaths = "steps")
    List<GoalJpaEntity> findByAreaIdOrderByPositionAscCreatedAtAsc(UUID areaId);

    @EntityGraph(attributePaths = "steps")
    List<GoalJpaEntity> findAllByOrderByPositionAscCreatedAtAsc();

    @EntityGraph(attributePaths = "steps")
    Optional<GoalJpaEntity> findWithStepsById(UUID id);
}
