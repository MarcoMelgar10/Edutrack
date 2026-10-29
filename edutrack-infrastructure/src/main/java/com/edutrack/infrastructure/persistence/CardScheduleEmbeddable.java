package com.edutrack.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/** The schedule of one side of a card: one {@code card_schedule} row, keyed by (card, side). */
@Embeddable
public class CardScheduleEmbeddable {

    @Column(name = "ease", nullable = false, precision = 4, scale = 2)
    private BigDecimal ease;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Column(name = "repetitions", nullable = false)
    private int repetitions;

    @Column(name = "lapses", nullable = false)
    private int lapses;

    @Column(name = "due_on", nullable = false)
    private LocalDate dueOn;

    @Column(name = "last_reviewed_on")
    private LocalDate lastReviewedOn;

    protected CardScheduleEmbeddable() {
    }

    public CardScheduleEmbeddable(BigDecimal ease, int intervalDays, int repetitions, int lapses, LocalDate dueOn,
                                  LocalDate lastReviewedOn) {
        this.ease = ease;
        this.intervalDays = intervalDays;
        this.repetitions = repetitions;
        this.lapses = lapses;
        this.dueOn = dueOn;
        this.lastReviewedOn = lastReviewedOn;
    }

    public BigDecimal getEase() {
        return ease;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public int getLapses() {
        return lapses;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public LocalDate getLastReviewedOn() {
        return lastReviewedOn;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CardScheduleEmbeddable schedule
                && ease.compareTo(schedule.ease) == 0
                && intervalDays == schedule.intervalDays
                && repetitions == schedule.repetitions
                && lapses == schedule.lapses
                && dueOn.equals(schedule.dueOn)
                && Objects.equals(lastReviewedOn, schedule.lastReviewedOn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ease.stripTrailingZeros(), intervalDays, repetitions, lapses, dueOn, lastReviewedOn);
    }
}
