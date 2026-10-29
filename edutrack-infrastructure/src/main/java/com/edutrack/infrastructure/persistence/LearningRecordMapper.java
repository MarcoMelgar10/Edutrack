package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.record.Assessment;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.Exercise;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.LearningRecordId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.Resource;
import com.edutrack.domain.record.ResourceId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

final class LearningRecordMapper {

    private LearningRecordMapper() {
    }

    /** Must run inside the transaction that loaded the entity, so the lazy lists can be read. */
    static LearningRecord toDomain(LearningRecordJpaEntity entity) {
        RecordOwner owner = entity.getStepId() != null
                ? RecordOwner.ofStep(new StepId(entity.getStepId()))
                : RecordOwner.ofGoal(new GoalId(entity.getGoalId()));
        return LearningRecord.restore(
                new LearningRecordId(entity.getId()),
                owner,
                entity.getNotes(),
                entity.getUpdatedAt(),
                entity.getResources().stream().map(LearningRecordMapper::toDomain).toList(),
                entity.getExercises().stream().map(LearningRecordMapper::toDomain).toList(),
                entity.getResults().stream().map(LearningRecordMapper::toDomain).toList());
    }

    private static Resource toDomain(LearningResourceJpaEntity entity) {
        return Resource.restore(new ResourceId(entity.getId()), entity.getTitle(), entity.getUrl(),
                entity.getType(), entity.isReviewed());
    }

    private static Exercise toDomain(LearningExerciseJpaEntity entity) {
        return Exercise.restore(new ExerciseId(entity.getId()), entity.getTitle(), entity.isDone(),
                entity.getDoneAt());
    }

    private static Assessment toDomain(LearningResultJpaEntity entity) {
        return Assessment.restore(new AssessmentId(entity.getId()), entity.getTitle(), entity.getScore(),
                entity.getMaxScore(), entity.getTakenOn(), entity.getNote(),
                entity.getMeasureId() == null ? null : new MeasureId(entity.getMeasureId()), entity.getPart());
    }

    /**
     * Brings the entity in line with the record. Like {@link GoalMapper}, each child list is
     * synchronized in place and never replaced: children that still exist keep their managed
     * entity (one instance per id), new ones get a new entity, missing ones are dropped (orphan
     * removal deletes them) and the list order follows the record order, stored as the position.
     */
    static void copyToEntity(LearningRecord record, LearningRecordJpaEntity entity) {
        entity.setGoalId(record.owner().isStep() ? null : record.owner().id());
        entity.setStepId(record.owner().isStep() ? record.owner().id() : null);
        entity.setNotes(record.notes());
        entity.setUpdatedAt(record.updatedAt());

        syncList(entity.getResources(), record.resources(),
                resource -> resource.id().value(),
                LearningResourceJpaEntity::getId,
                LearningResourceJpaEntity::new,
                (child, position, source) -> {
                    child.setRecord(entity);
                    child.setPosition(position);
                    child.setTitle(source.title());
                    child.setUrl(source.url());
                    child.setType(source.type());
                    child.setReviewed(source.reviewed());
                });

        syncList(entity.getExercises(), record.exercises(),
                exercise -> exercise.id().value(),
                LearningExerciseJpaEntity::getId,
                LearningExerciseJpaEntity::new,
                (child, position, source) -> {
                    child.setRecord(entity);
                    child.setPosition(position);
                    child.setTitle(source.title());
                    child.setDone(source.done());
                    child.setDoneAt(source.doneAt());
                });

        syncList(entity.getResults(), record.assessments(),
                assessment -> assessment.id().value(),
                LearningResultJpaEntity::getId,
                LearningResultJpaEntity::new,
                (child, position, source) -> {
                    child.setRecord(entity);
                    child.setPosition(position);
                    child.setTitle(source.title());
                    child.setScore(source.score());
                    child.setMaxScore(source.maxScore());
                    child.setTakenOn(source.takenOn());
                    child.setNote(source.note());
                    child.setMeasureId(source.measureId() == null ? null : source.measureId().value());
                    child.setPart(source.part());
                });
    }

    @FunctionalInterface
    private interface Copier<E, D> {
        void copy(E entity, int position, D source);
    }

    private static <E, D> void syncList(List<E> managed, List<D> domain, Function<D, UUID> domainId,
                                        Function<E, UUID> entityId, Function<UUID, E> create,
                                        Copier<E, D> copier) {
        Map<UUID, E> existing = new HashMap<>();
        for (E child : managed) {
            existing.put(entityId.apply(child), child);
        }
        List<E> ordered = new ArrayList<>(domain.size());
        int position = 0;
        for (D source : domain) {
            UUID id = domainId.apply(source);
            E child = existing.get(id);
            if (child == null) {
                child = create.apply(id);
            }
            copier.copy(child, position++, source);
            ordered.add(child);
        }
        managed.clear();
        managed.addAll(ordered);
    }
}
