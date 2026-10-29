package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.CompetencyRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaCompetencyRepositoryAdapter implements CompetencyRepository {

    private final SpringDataCompetencyRepository repository;

    JpaCompetencyRepositoryAdapter(SpringDataCompetencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Competency save(Competency competency) {
        CompetencyJpaEntity entity = repository.findWithDetailsById(competency.id().value())
                .orElseGet(() -> new CompetencyJpaEntity(competency.id().value()));
        CompetencyMapper.copyToEntity(competency, entity);
        return CompetencyMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Competency> findById(CompetencyId id) {
        return repository.findWithDetailsById(id.value()).map(CompetencyMapper::toDomain);
    }

    /** Deleting a missing competency does nothing. */
    @Override
    @Transactional
    public void delete(CompetencyId id) {
        repository.deleteById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competency> findByGoal(GoalId goalId) {
        return repository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId.value()).stream()
                .map(CompetencyMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competency> findByStep(StepId stepId) {
        return repository.findByStep(stepId.value()).stream()
                .map(CompetencyMapper::toDomain)
                .toList();
    }
}
