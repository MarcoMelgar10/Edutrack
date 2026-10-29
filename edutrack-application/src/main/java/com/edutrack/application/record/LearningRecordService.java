package com.edutrack.application.record;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureRepository;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.ResourceDetails;
import com.edutrack.domain.record.ResourceId;
import com.edutrack.domain.shared.DomainValidationException;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.function.Consumer;

/**
 * Use cases for the learning record of a step (notes, resources, exercises, results) or of a
 * goal (results). Opening never writes: a record is created the first time something is saved
 * for its owner. Every change returns the refreshed {@link RecordView}.
 *
 * <p>A result may be recorded against a measure of the owner's goal. That spans two aggregates, so
 * it is checked here: the measure must exist in the same goal and the score cannot exceed its maximum.
 */
public class LearningRecordService {

    private final LearningRecordRepository records;
    private final GoalRepository goals;
    private final MeasureRepository measures;
    private final Clock clock;

    public LearningRecordService(LearningRecordRepository records, GoalRepository goals, MeasureRepository measures,
                                 Clock clock) {
        this.records = records;
        this.goals = goals;
        this.measures = measures;
        this.clock = clock;
    }

    public RecordView open(RecordOwner owner) {
        return records.findByOwner(owner).map(RecordView::of).orElseGet(() -> RecordView.empty(owner));
    }

    public RecordView saveNotes(RecordOwner owner, String markdown) {
        return change(owner, record -> record.updateNotes(markdown, clock.instant()));
    }

    public RecordView addResource(RecordOwner owner, ResourceDetails details) {
        return change(owner, record -> record.addResource(details, clock.instant()));
    }

    public RecordView updateResource(RecordOwner owner, ResourceId resourceId, ResourceDetails details) {
        return change(owner, record -> record.updateResource(resourceId, details, clock.instant()));
    }

    public RecordView markResourceReviewed(RecordOwner owner, ResourceId resourceId, boolean reviewed) {
        return change(owner, record -> record.markResourceReviewed(resourceId, reviewed, clock.instant()));
    }

    public RecordView removeResource(RecordOwner owner, ResourceId resourceId) {
        return change(owner, record -> record.removeResource(resourceId, clock.instant()));
    }

    public RecordView addExercise(RecordOwner owner, String title) {
        return change(owner, record -> record.addExercise(title, clock.instant()));
    }

    public RecordView renameExercise(RecordOwner owner, ExerciseId exerciseId, String title) {
        return change(owner, record -> record.renameExercise(exerciseId, title, clock.instant()));
    }

    public RecordView markExerciseDone(RecordOwner owner, ExerciseId exerciseId, boolean done) {
        return change(owner, record -> record.markExerciseDone(exerciseId, done, clock.instant()));
    }

    public RecordView moveExercise(RecordOwner owner, ExerciseId exerciseId, MoveDirection direction) {
        return change(owner, record -> record.moveExercise(exerciseId, direction, clock.instant()));
    }

    public RecordView removeExercise(RecordOwner owner, ExerciseId exerciseId) {
        return change(owner, record -> record.removeExercise(exerciseId, clock.instant()));
    }

    public RecordView addResult(RecordOwner owner, AssessmentDetails details) {
        checkMeasure(owner, details);
        return change(owner, record -> record.addAssessment(details, LocalDate.now(clock), clock.instant()));
    }

    public RecordView updateResult(RecordOwner owner, AssessmentId resultId, AssessmentDetails details) {
        checkMeasure(owner, details);
        return change(owner, record -> record.updateAssessment(resultId, details, LocalDate.now(clock), clock.instant()));
    }

    public RecordView removeResult(RecordOwner owner, AssessmentId resultId) {
        return change(owner, record -> record.removeAssessment(resultId, clock.instant()));
    }

    private void checkMeasure(RecordOwner owner, AssessmentDetails details) {
        if (details.measureId() == null) {
            return;
        }
        Measure measure = measures.findById(details.measureId())
                .orElseThrow(() -> new NotFoundException("Measure", details.measureId()));
        if (!measure.goalId().equals(goalOf(owner))) {
            throw new NotFoundException("Measure", details.measureId());
        }
        if (measure.maxValue() != null && details.score() != null
                && details.score().setScale(2, RoundingMode.HALF_UP).compareTo(measure.maxValue()) > 0) {
            throw new DomainValidationException("score-above-max", "Score cannot be higher than the maximum score.");
        }
    }

    /** The goal a record belongs to: itself for a goal record, the goal holding the step for a step record. */
    private GoalId goalOf(RecordOwner owner) {
        if (!owner.isStep()) {
            return new GoalId(owner.id());
        }
        return goals.findAll().stream()
                .filter(goal -> goal.steps().stream().map(RoadmapStep::id).anyMatch(id -> id.value().equals(owner.id())))
                .map(Goal::id)
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Step", owner.id()));
    }

    private RecordView change(RecordOwner owner, Consumer<LearningRecord> change) {
        LearningRecord record = records.findByOwner(owner)
                .orElseGet(() -> LearningRecord.create(owner, clock.instant()));
        change.accept(record);
        return RecordView.of(records.save(record));
    }
}
