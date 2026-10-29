package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.journal.JournalEntry;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalEntryRepository;
import com.edutrack.domain.journal.JournalFilter;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaJournalEntryRepositoryAdapter implements JournalEntryRepository {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("writtenOn"), Sort.Order.desc("createdAt"));

    private final SpringDataJournalEntryRepository repository;

    JpaJournalEntryRepositoryAdapter(SpringDataJournalEntryRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public JournalEntry save(JournalEntry entry) {
        JournalEntryJpaEntity entity = repository.findById(entry.id().value())
                .orElseGet(() -> new JournalEntryJpaEntity(entry.id().value()));
        JournalEntryMapper.copyToEntity(entry, entity);
        return JournalEntryMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<JournalEntry> findById(JournalEntryId id) {
        return repository.findById(id.value()).map(JournalEntryMapper::toDomain);
    }

    /** Deleting a missing entry does nothing. */
    @Override
    @Transactional
    public void delete(JournalEntryId id) {
        repository.deleteById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntry> find(JournalFilter filter) {
        Specification<JournalEntryJpaEntity> matching = Specification.unrestricted();
        if (filter.areaId() != null) {
            matching = matching.and((root, query, builder) -> builder.equal(root.get("areaId"), filter.areaId().value()));
        }
        if (filter.goalId() != null) {
            matching = matching.and((root, query, builder) -> builder.equal(root.get("goalId"), filter.goalId().value()));
        }
        if (filter.kind() != null) {
            matching = matching.and((root, query, builder) -> builder.equal(root.get("kind"), filter.kind()));
        }
        return repository.findAll(matching, NEWEST_FIRST).stream().map(JournalEntryMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntry> findWrittenBetween(LocalDate from, LocalDate to) {
        return repository.findByWrittenOnBetweenOrderByWrittenOnAscCreatedAtAsc(from, to).stream()
                .map(JournalEntryMapper::toDomain)
                .toList();
    }
}
