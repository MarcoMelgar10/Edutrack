package com.edutrack.domain.review;

import java.time.Instant;
import java.util.Objects;

/**
 * One answered card side: when, how it went and how long it took. Appended to the review history
 * and never changed; the daily limits and the study streak are counted from it.
 *
 * @param wasNew whether this was the first time the side was shown (it counts against the daily new-card limit)
 */
public record ReviewLog(ReviewLogId id, CardId cardId, CardSide side, Instant reviewedAt, Grade grade, int seconds,
                        boolean wasNew) {

    public ReviewLog {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(cardId, "cardId");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(reviewedAt, "reviewedAt");
        Objects.requireNonNull(grade, "grade");
        if (seconds < 0) {
            throw new IllegalArgumentException("Seconds cannot be negative");
        }
    }
}
