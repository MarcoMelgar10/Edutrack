package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.record.MeasuredResult;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaLearningRecordRepositoryAdapter implements LearningRecordRepository {

    private final SpringDataLearningRecordRepository repository;

    JpaLearningRecordRepositoryAdapter(SpringDataLearningRecordRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<LearningRecord> findByOwner(RecordOwner owner) {
        return load(owner).map(LearningRecordMapper::toDomain);
    }

    @Override
    @Transactional
    public LearningRecord save(LearningRecord record) {
        // Look the record up by id: a record saved before has the same id, a new one does not exist yet.
        // A second new record for an owner that already has one is left to the database to reject.
        LearningRecordJpaEntity entity = repository.findById(record.id().value())
                .orElseGet(() -> new LearningRecordJpaEntity(record.id().value()));
        LearningRecordMapper.copyToEntity(record, entity);
        return LearningRecordMapper.toDomain(repository.saveAndFlush(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public Map<StepId, RecordSummary> summariesForSteps(Collection<StepId> stepIds) {
        if (stepIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = stepIds.stream().map(StepId::value).toList();
        Map<StepId, RecordSummary> summaries = new HashMap<>();
        for (SpringDataLearningRecordRepository.SummaryRow row : repository.summariesForSteps(ids)) {
            summaries.put(new StepId(row.getStepId()), new RecordSummary(row.getHasNotes(),
                    (int) row.getResourceCount(), (int) row.getExercisesDone(),
                    (int) row.getExercisesTotal(), (int) row.getResultCount()));
        }
        return summaries;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeasuredResult> resultsForMeasure(MeasureId measureId) {
        return repository.resultsForMeasure(measureId.value()).stream()
                .map(row -> new MeasuredResult(
                        row.getStepId() != null
                                ? RecordOwner.ofStep(new StepId(row.getStepId()))
                                : RecordOwner.ofGoal(new GoalId(row.getGoalId())),
                        new AssessmentId(row.getId()), row.getTitle(), row.getPart(), row.getScore(),
                        row.getTakenOn(), row.getPosition()))
                .toList();
    }

    private Optional<LearningRecordJpaEntity> load(RecordOwner owner) {
        return owner.isStep() ? repository.findByStepId(owner.id()) : repository.findByGoalId(owner.id());
    }
}
