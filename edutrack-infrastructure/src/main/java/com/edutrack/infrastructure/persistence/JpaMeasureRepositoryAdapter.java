package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaMeasureRepositoryAdapter implements MeasureRepository {

    private final SpringDataMeasureRepository repository;

    JpaMeasureRepositoryAdapter(SpringDataMeasureRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Measure save(Measure measure) {
        MeasureJpaEntity entity = repository.findById(measure.id().value())
                .orElseGet(() -> new MeasureJpaEntity(measure.id().value()));
        MeasureMapper.copyToEntity(measure, entity);
        return MeasureMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Measure> findById(MeasureId id) {
        return repository.findById(id.value()).map(MeasureMapper::toDomain);
    }

    /** The database unlinks the measure's results (ON DELETE SET NULL); a missing measure is ignored. */
    @Override
    @Transactional
    public void delete(MeasureId id) {
        repository.deleteById(id.value());
        repository.flush();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Measure> findByGoal(GoalId goalId) {
        return repository.findByGoalIdOrderByPositionAscCreatedAtAsc(goalId.value()).stream()
                .map(MeasureMapper::toDomain)
                .toList();
    }
}
