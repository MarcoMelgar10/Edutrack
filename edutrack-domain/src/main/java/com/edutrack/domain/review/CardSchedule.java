package com.edutrack.domain.review;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Where a card stands in its spaced-repetition cycle. Immutable: {@link Sm2Scheduler} returns
 * the next schedule after each review.
 *
 * @param ease           SM-2 easiness factor, two decimals, never below {@link Sm2Scheduler#MIN_EASE}
 * @param intervalDays   days between the last review and {@code dueOn}; 0 for a new or lapsed card
 * @param repetitions    successful reviews in a row (reset by a lapse)
 * @param lapses         times the card was forgotten after being learned
 * @param dueOn          the day the card is next shown
 * @param lastReviewedOn {@code null} while the card has never been reviewed (a new card)
 */
public record CardSchedule(BigDecimal ease, int intervalDays, int repetitions, int lapses, LocalDate dueOn,
                           LocalDate lastReviewedOn) {

    public CardSchedule {
        Objects.requireNonNull(ease, "ease");
        Objects.requireNonNull(dueOn, "dueOn");
        if (ease.compareTo(Sm2Scheduler.MIN_EASE) < 0) {
            throw new IllegalArgumentException("Ease must be at least " + Sm2Scheduler.MIN_EASE + ", was " + ease);
        }
        if (intervalDays < 0 || repetitions < 0 || lapses < 0) {
            throw new IllegalArgumentException("Interval, repetitions and lapses cannot be negative");
        }
        ease = ease.setScale(2, RoundingMode.HALF_UP);
    }

    /** A card nobody has studied yet: default ease, available from {@code today}. */
    public static CardSchedule newCard(LocalDate today) {
        return new CardSchedule(Sm2Scheduler.INITIAL_EASE, 0, 0, 0, today, null);
    }

    public boolean isNew() {
        return lastReviewedOn == null;
    }

    public boolean isDueOn(LocalDate day) {
        return !dueOn.isAfter(day);
    }
}
