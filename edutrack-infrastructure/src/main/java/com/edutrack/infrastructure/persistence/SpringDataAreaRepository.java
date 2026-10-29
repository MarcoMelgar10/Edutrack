package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface SpringDataAreaRepository extends JpaRepository<AreaJpaEntity, UUID> {

    List<AreaJpaEntity> findAllByOrderByPositionAscCreatedAtAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
