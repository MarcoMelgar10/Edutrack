package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface SpringDataCompetencyRepository extends JpaRepository<CompetencyJpaEntity, UUID> {

    @EntityGraph(attributePaths = {"history", "stepIds"})
    Optional<CompetencyJpaEntity> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"history", "stepIds"})
    List<CompetencyJpaEntity> findByGoalIdOrderByPositionAscCreatedAtAsc(UUID goalId);

    @EntityGraph(attributePaths = {"history", "stepIds"})
    @Query("select c from CompetencyJpaEntity c where :stepId member of c.stepIds order by c.position, c.createdAt")
    List<CompetencyJpaEntity> findByStep(@Param("stepId") UUID stepId);
}
