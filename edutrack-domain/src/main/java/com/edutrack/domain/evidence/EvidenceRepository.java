package com.edutrack.domain.evidence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.util.List;
import java.util.Optional;

/** Port for storing evidence. Implemented by the infrastructure layer. */
public interface EvidenceRepository {

    Evidence save(Evidence evidence);

    Optional<Evidence> findById(EvidenceId id);

    void delete(EvidenceId id);

    /** Evidence of a goal, newest {@code takenOn} first (ties: most recently added first). */
    List<Evidence> findByGoal(GoalId goalId);

    /** Evidence linked to a step, in the same order. */
    List<Evidence> findByStep(StepId stepId);
}
