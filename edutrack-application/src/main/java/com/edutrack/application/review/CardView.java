package com.edutrack.application.review;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardSchedule;
import com.edutrack.domain.review.CardSide;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * A card as the card list shows it: its content and, for each side it has, when it is next due.
 *
 * @param sides one entry per side the card has (FORWARD always, REVERSE only while reversible)
 */
public record CardView(CardId id, GoalId goalId, StepId stepId, String front, String back, Set<String> tags,
                       boolean reversible, Instant createdAt, Map<CardSide, SideView> sides) {

    /** {@code isNew} is true until the side is reviewed for the first time. */
    public record SideView(LocalDate nextDue, boolean isNew, int intervalDays, int repetitions, int lapses,
                           BigDecimal ease) {

        static SideView of(CardSchedule schedule) {
            return new SideView(schedule.dueOn(), schedule.isNew(), schedule.intervalDays(),
                    schedule.repetitions(), schedule.lapses(), schedule.ease());
        }
    }

    static CardView of(Card card) {
        Map<CardSide, SideView> sides = new EnumMap<>(CardSide.class);
        card.schedules().forEach((side, schedule) -> sides.put(side, SideView.of(schedule)));
        return new CardView(card.id(), card.goalId(), card.stepId(), card.front(), card.back(), card.tags(),
                card.reversible(), card.createdAt(), Collections.unmodifiableMap(sides));
    }

    /** The earliest day any side of the card is due. */
    public LocalDate nextDue() {
        return sides.values().stream().map(SideView::nextDue).min(LocalDate::compareTo).orElseThrow();
    }

    /** Whether no side of the card has been studied yet. */
    public boolean isNew() {
        return sides.values().stream().allMatch(SideView::isNew);
    }
}
