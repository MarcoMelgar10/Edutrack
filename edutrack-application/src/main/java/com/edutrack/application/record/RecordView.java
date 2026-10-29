package com.edutrack.application.record;

import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.Assessment;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.Exercise;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.Resource;
import com.edutrack.domain.record.ResourceId;
import com.edutrack.domain.record.ResourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Everything the step page shows: notes, resources, exercises and results, plus the counts
 * for the tab titles. Results are listed newest first.
 */
public record RecordView(RecordOwner owner, String notes, Instant updatedAt, List<ResourceView> resources,
                         List<ExerciseView> exercises, List<ResultView> results, RecordSummary summary) {

    public record ResourceView(ResourceId id, String title, String url, ResourceType type, boolean reviewed) {
    }

    public record ExerciseView(ExerciseId id, String title, boolean done, Instant doneAt) {
    }

    /**
     * {@code percentage} is empty when the result has no maximum score. {@code measureId} and
     * {@code part} are set when the result counts towards a goal's measure.
     */
    public record ResultView(AssessmentId id, String title, BigDecimal score, BigDecimal maxScore,
                             LocalDate takenOn, String note, Progress percentage, MeasureId measureId, String part) {

        public ResultView(AssessmentId id, String title, BigDecimal score, BigDecimal maxScore,
                          LocalDate takenOn, String note, Progress percentage) {
            this(id, title, score, maxScore, takenOn, note, percentage, null, null);
        }
    }

    /** What an owner without any saved learning yet looks like. */
    static RecordView empty(RecordOwner owner) {
        return new RecordView(owner, "", null, List.of(), List.of(), List.of(), RecordSummary.EMPTY);
    }

    static RecordView of(LearningRecord record) {
        return new RecordView(record.owner(), record.notes(), record.updatedAt(),
                record.resources().stream().map(RecordView::resource).toList(),
                record.exercises().stream().map(RecordView::exercise).toList(),
                record.assessments().stream()
                        .sorted(Comparator.comparing(Assessment::takenOn).reversed())
                        .map(RecordView::result)
                        .toList(),
                record.summary());
    }

    private static ResourceView resource(Resource resource) {
        return new ResourceView(resource.id(), resource.title(), resource.url(), resource.type(), resource.reviewed());
    }

    private static ExerciseView exercise(Exercise exercise) {
        return new ExerciseView(exercise.id(), exercise.title(), exercise.done(), exercise.doneAt());
    }

    private static ResultView result(Assessment assessment) {
        return new ResultView(assessment.id(), assessment.title(), assessment.score(), assessment.maxScore(),
                assessment.takenOn(), assessment.note(), assessment.percentage().orElse(null),
                assessment.measureId(), assessment.part());
    }
}
