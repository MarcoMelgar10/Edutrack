package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataEvidenceRepository extends JpaRepository<EvidenceJpaEntity, UUID> {

    List<EvidenceJpaEntity> findByGoalIdOrderByTakenOnDescCreatedAtDesc(UUID goalId);

    List<EvidenceJpaEntity> findByStepIdOrderByTakenOnDescCreatedAtDesc(UUID stepId);
}
