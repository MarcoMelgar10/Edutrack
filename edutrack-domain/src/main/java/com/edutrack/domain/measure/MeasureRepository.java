package com.edutrack.domain.measure;

import com.edutrack.domain.goal.GoalId;

import java.util.List;
import java.util.Optional;

/** Port for storing measures. Implemented by the infrastructure layer. */
public interface MeasureRepository {

    Measure save(Measure measure);

    Optional<Measure> findById(MeasureId id);

    /** Deleting a measure keeps the results recorded against it; they just stop being linked. A missing measure is ignored. */
    void delete(MeasureId id);

    /** The measures of one goal, ordered by position. */
    List<Measure> findByGoal(GoalId goalId);
}
