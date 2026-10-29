package com.edutrack.application.review;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardSide;

/**
 * One card side waiting in the review queue. {@code prompt} is shown first and {@code answer} on
 * reveal; for the reverse side they are the card's back and front. Both are Markdown.
 */
public record ReviewItem(CardId cardId, CardSide side, String prompt, String answer, String goalTitle,
                         String areaName, boolean isNew, GoalId goalId) {

    /** Without the goal id, for callers that only show the item. */
    public ReviewItem(CardId cardId, CardSide side, String prompt, String answer, String goalTitle,
                      String areaName, boolean isNew) {
        this(cardId, side, prompt, answer, goalTitle, areaName, isNew, null);
    }
}
