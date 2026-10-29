package com.edutrack.domain.review;

import com.edutrack.domain.goal.StepId;

import java.util.Set;

/**
 * The editable content of a flashcard as typed in the card dialog. {@code stepId} optionally files
 * the card under a roadmap step of the card's goal; {@code front} and {@code back} are Markdown.
 * Validated by {@link Card}.
 */
public record CardDetails(StepId stepId, String front, String back, Set<String> tags, boolean reversible) {
}
