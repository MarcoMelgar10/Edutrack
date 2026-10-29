package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.evidence.Evidence;
import com.edutrack.domain.evidence.EvidenceId;
import com.edutrack.domain.evidence.EvidenceRepository;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaEvidenceRepositoryAdapter implements EvidenceRepository {

    private final SpringDataEvidenceRepository repository;

    JpaEvidenceRepositoryAdapter(SpringDataEvidenceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Evidence save(Evidence evidence) {
        EvidenceJpaEntity entity = repository.findById(evidence.id().value())
                .orElseGet(() -> new EvidenceJpaEntity(evidence.id().value()));
        EvidenceMapper.copyToEntity(evidence, entity);
        return EvidenceMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Evidence> findById(EvidenceId id) {
        return repository.findById(id.value()).map(EvidenceMapper::toDomain);
    }

    @Override
    @Transactional
    public void delete(EvidenceId id) {
        repository.deleteById(id.value());
        repository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Evidence> findByGoal(GoalId goalId) {
        return repository.findByGoalIdOrderByTakenOnDescCreatedAtDesc(goalId.value()).stream()
                .map(EvidenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Evidence> findByStep(StepId stepId) {
        return repository.findByStepIdOrderByTakenOnDescCreatedAtDesc(stepId.value()).stream()
                .map(EvidenceMapper::toDomain).toList();
    }
}
