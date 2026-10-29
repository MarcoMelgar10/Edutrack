package com.edutrack.domain.practice;

import com.edutrack.domain.goal.GoalId;

import java.util.List;
import java.util.Optional;

/** Port for storing practice routines. Implemented by the infrastructure layer. */
public interface PracticeRoutineRepository {

    PracticeRoutine save(PracticeRoutine routine);

    Optional<PracticeRoutine> findById(PracticeRoutineId id);

    /** Deleting a missing routine does nothing. */
    void delete(PracticeRoutineId id);

    /** Routines of one goal, active or paused, ordered by position. */
    List<PracticeRoutine> findByGoal(GoalId goalId);

    /** Active routines of every goal (for Today), ordered by position. */
    List<PracticeRoutine> findActive();
}
