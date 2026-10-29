package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataMeasureRepository extends JpaRepository<MeasureJpaEntity, UUID> {

    List<MeasureJpaEntity> findByGoalIdOrderByPositionAscCreatedAtAsc(UUID goalId);
}
