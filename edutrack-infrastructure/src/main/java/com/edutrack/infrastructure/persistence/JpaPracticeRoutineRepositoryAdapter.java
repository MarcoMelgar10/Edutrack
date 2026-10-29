package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.practice.PracticeRoutineRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaPracticeRoutineRepositoryAdapter implements PracticeRoutineRepository {

    private final SpringDataPracticeRoutineRepository repository;

    JpaPracticeRoutineRepositoryAdapter(SpringDataPracticeRoutineRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public PracticeRoutine save(PracticeRoutine routine) {
        PracticeRoutineJpaEntity entity = repository.findById(routine.id().value())
                .orElseGet(() -> new PracticeRoutineJpaEntity(routine.id().value()));
        PracticeRoutineMapper.copyToEntity(routine, entity);
        return PracticeRoutineMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PracticeRoutine> findById(PracticeRoutineId id) {
        return repository.findById(id.value()).map(PracticeRoutineMapper::toDomain);
    }

    /** The database keeps the routine's sessions and clears their routine link. Deleting a missing routine does nothing. */
    @Override
    @Transactional
    public void delete(PracticeRoutineId id) {
        repository.deleteById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticeRoutine> findByGoal(GoalId goalId) {
        return repository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId.value()).stream()
                .map(PracticeRoutineMapper::toDomain)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PracticeRoutine> findActive() {
        return repository.findByActiveTrueOrderByPositionAscCreatedAtAsc().stream()
                .map(PracticeRoutineMapper::toDomain)
                .toList();
    }
}
