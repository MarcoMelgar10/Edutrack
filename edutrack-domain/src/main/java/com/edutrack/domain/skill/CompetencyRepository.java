package com.edutrack.domain.skill;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.util.List;
import java.util.Optional;

/** Port for storing competencies with their level history and linked steps. Implemented by the infrastructure layer. */
public interface CompetencyRepository {

    /** Saves the competency with its whole history and step links (added, changed and removed). */
    Competency save(Competency competency);

    Optional<Competency> findById(CompetencyId id);

    /** Deleting a missing competency does nothing. */
    void delete(CompetencyId id);

    /** The competencies of one goal, ordered by position. */
    List<Competency> findByGoal(GoalId goalId);

    /** The competencies a roadmap step contributes to, ordered by position. */
    List<Competency> findByStep(StepId stepId);
}
