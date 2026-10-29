package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.attachment.AttachmentRepository;
import com.edutrack.domain.record.RecordOwner;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public class JpaAttachmentRepositoryAdapter implements AttachmentRepository {

    private final SpringDataAttachmentRepository repository;

    JpaAttachmentRepositoryAdapter(SpringDataAttachmentRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Attachment save(Attachment attachment) {
        AttachmentJpaEntity entity = repository.findById(attachment.id().value())
                .orElseGet(() -> new AttachmentJpaEntity(attachment.id().value()));
        AttachmentMapper.copyToEntity(attachment, entity);
        return AttachmentMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Attachment> findById(AttachmentId id) {
        return repository.findById(id.value()).map(AttachmentMapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(AttachmentId id) {
        repository.deleteById(id.value());
        repository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Attachment> findByOwner(RecordOwner owner) {
        List<AttachmentJpaEntity> rows = owner.isStep()
                ? repository.findByStepIdOrderByAddedAtDesc(owner.id())
                : repository.findByGoalIdOrderByAddedAtDesc(owner.id());
        return rows.stream().map(AttachmentMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> allStoredPaths() {
        return new HashSet<>(repository.findAllStoredPaths());
    }
}
