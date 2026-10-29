package com.edutrack.domain.record;

import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.measure.MeasureId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port for storing learning records. Implemented by the infrastructure layer. */
public interface LearningRecordRepository {

    Optional<LearningRecord> findByOwner(RecordOwner owner);

    /** Saves the record with all its resources, exercises and results (added, changed, reordered, removed). */
    LearningRecord save(LearningRecord record);

    /**
     * Summaries for the given steps in one query (for the roadmap timeline). Steps without a
     * record are absent from the map; callers use {@link RecordSummary#EMPTY} for them.
     */
    Map<StepId, RecordSummary> summariesForSteps(Collection<StepId> stepIds);

    /** Every result recorded against the measure, in any record, oldest first (then by entry order). */
    List<MeasuredResult> resultsForMeasure(MeasureId measureId);
}
