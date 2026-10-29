package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaGoalRepositoryAdapter implements GoalRepository {

    private final SpringDataGoalRepository repository;

    JpaGoalRepositoryAdapter(SpringDataGoalRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Goal> findByArea(AreaId areaId) {
        return repository.findByAreaIdOrderByPositionAscCreatedAtAsc(areaId.value()).stream()
                .map(GoalMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Goal> findAll() {
        return repository.findAllByOrderByPositionAscCreatedAtAsc().stream()
                .map(GoalMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Goal> findById(GoalId id) {
        return repository.findWithStepsById(id.value()).map(GoalMapper::toDomain);
    }

    @Override
    @Transactional
    public Goal save(Goal goal) {
        GoalJpaEntity entity = repository.findWithStepsById(goal.id().value())
                .orElseGet(() -> new GoalJpaEntity(goal.id().value()));
        GoalMapper.copyToEntity(goal, entity);
        return GoalMapper.toDomain(repository.saveAndFlush(entity));
    }

    /** Deleting a missing goal does nothing. */
    @Override
    @Transactional
    public void delete(GoalId id) {
        repository.deleteById(id.value());
    }
}
