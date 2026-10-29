package com.edutrack.domain.review;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Port for storing flashcards with their schedules. Implemented by the infrastructure layer. */
public interface CardRepository {

    Card save(Card card);

    Optional<Card> findById(CardId id);

    /** Deleting a missing card does nothing; its review history goes with it. */
    void delete(CardId id);

    /** The cards of one goal, oldest first. */
    List<Card> findByGoal(GoalId goalId);

    /** The cards filed under one roadmap step, oldest first. */
    List<Card> findByStep(StepId stepId);

    /** Cards with at least one side due on or before {@code day}, or never studied; oldest first. */
    List<Card> findDueOrNew(LocalDate day);

    long countByGoal(GoalId goalId);
}
