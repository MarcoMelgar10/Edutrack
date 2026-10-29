package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface SpringDataAttachmentRepository extends JpaRepository<AttachmentJpaEntity, UUID> {

    List<AttachmentJpaEntity> findByGoalIdOrderByAddedAtDesc(UUID goalId);

    List<AttachmentJpaEntity> findByStepIdOrderByAddedAtDesc(UUID stepId);

    @Query("select a.storedPath from AttachmentJpaEntity a")
    List<String> findAllStoredPaths();
}
