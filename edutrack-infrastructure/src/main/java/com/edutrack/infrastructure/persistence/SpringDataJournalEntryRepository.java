package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Specifications carry the optional filter criteria: a derived query cannot skip a criterion that is {@code null}. */
interface SpringDataJournalEntryRepository
        extends JpaRepository<JournalEntryJpaEntity, UUID>, JpaSpecificationExecutor<JournalEntryJpaEntity> {

    List<JournalEntryJpaEntity> findByWrittenOnBetweenOrderByWrittenOnAscCreatedAtAsc(LocalDate from, LocalDate to);
}
