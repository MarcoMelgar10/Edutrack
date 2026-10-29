package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardSchedule;
import com.edutrack.domain.review.CardSide;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class CardMapper {

    private CardMapper() {
    }

    /** Must run inside the transaction that loaded the entity, so the lazy schedules can be read. */
    static Card toDomain(CardJpaEntity entity) {
        Map<CardSide, CardSchedule> schedules = new EnumMap<>(CardSide.class);
        entity.getSchedules().forEach((side, schedule) -> schedules.put(side, new CardSchedule(
                schedule.getEase(), schedule.getIntervalDays(), schedule.getRepetitions(), schedule.getLapses(),
                schedule.getDueOn(), schedule.getLastReviewedOn())));
        Set<String> tags = new TreeSet<>(Arrays.asList(entity.getTags()));
        return Card.restore(
                new CardId(entity.getId()),
                new GoalId(entity.getGoalId()),
                new CardDetails(entity.getStepId() == null ? null : new StepId(entity.getStepId()),
                        entity.getFront(), entity.getBack(), tags, entity.isReversible()),
                entity.getCreatedAt(),
                schedules);
    }

    /** Brings the entity in line with the card; the schedule map is changed in place, never replaced. */
    static void copyToEntity(Card card, CardJpaEntity entity) {
        entity.setGoalId(card.goalId().value());
        entity.setStepId(card.stepId() == null ? null : card.stepId().value());
        entity.setFront(card.front());
        entity.setBack(card.back());
        entity.setTags(card.tags().toArray(new String[0]));
        entity.setReversible(card.reversible());
        entity.setCreatedAt(card.createdAt());

        entity.getSchedules().keySet().retainAll(card.schedules().keySet());
        card.schedules().forEach((side, schedule) -> entity.getSchedules().put(side, new CardScheduleEmbeddable(
                schedule.ease(), schedule.intervalDays(), schedule.repetitions(), schedule.lapses(),
                schedule.dueOn(), schedule.lastReviewedOn())));
    }
}
