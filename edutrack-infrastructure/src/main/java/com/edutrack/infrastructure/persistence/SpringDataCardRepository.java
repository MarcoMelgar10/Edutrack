package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCardRepository extends JpaRepository<CardJpaEntity, UUID> {

    @EntityGraph(attributePaths = "schedules")
    Optional<CardJpaEntity> findWithSchedulesById(UUID id);

    @EntityGraph(attributePaths = "schedules")
    List<CardJpaEntity> findByGoalIdOrderByCreatedAtAscIdAsc(UUID goalId);

    @EntityGraph(attributePaths = "schedules")
    List<CardJpaEntity> findByStepIdOrderByCreatedAtAscIdAsc(UUID stepId);

    /** The condition is a subquery, not a join, so the schedules loaded for each card are all of its sides. */
    @EntityGraph(attributePaths = "schedules")
    @Query("""
            select c from CardJpaEntity c
            where exists (select 1 from c.schedules s where s.dueOn <= :day or s.lastReviewedOn is null)
            order by c.createdAt, c.id
            """)
    List<CardJpaEntity> findDueOrNew(@Param("day") LocalDate day);

    long countByGoalId(UUID goalId);
}
