package com.edutrack.domain.goal;

import com.edutrack.domain.area.AreaId;

import java.util.List;
import java.util.Optional;

/** Port for storing goals together with their roadmap steps. Implemented by the infrastructure layer. */
public interface GoalRepository {

    /** Goals of one area, ordered by position, each with its steps loaded. */
    List<Goal> findByArea(AreaId areaId);

    /** Every goal of every area, each with its steps loaded (for Today, Insights and the review queue). */
    List<Goal> findAll();

    Optional<Goal> findById(GoalId id);

    /** Saves the goal and its whole roadmap (added, changed, reordered and removed steps). */
    Goal save(Goal goal);

    void delete(GoalId id);
}
