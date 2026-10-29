package com.edutrack.application.support;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardRepository;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class InMemoryCardRepository implements CardRepository {

    private final Map<CardId, Card> cards = new LinkedHashMap<>();

    @Override
    public Card save(Card card) {
        cards.put(card.id(), card);
        return card;
    }

    @Override
    public Optional<Card> findById(CardId id) {
        return Optional.ofNullable(cards.get(id));
    }

    @Override
    public void delete(CardId id) {
        cards.remove(id);
    }

    @Override
    public List<Card> findByGoal(GoalId goalId) {
        return oldestFirst(cards.values().stream().filter(card -> card.goalId().equals(goalId)));
    }

    @Override
    public List<Card> findByStep(StepId stepId) {
        return oldestFirst(cards.values().stream().filter(card -> stepId.equals(card.stepId())));
    }

    @Override
    public List<Card> findDueOrNew(LocalDate day) {
        return oldestFirst(cards.values().stream()
                .filter(card -> card.schedules().values().stream()
                        .anyMatch(schedule -> schedule.isNew() || schedule.isDueOn(day))));
    }

    @Override
    public long countByGoal(GoalId goalId) {
        return cards.values().stream().filter(card -> card.goalId().equals(goalId)).count();
    }

    public int size() {
        return cards.size();
    }

    private static List<Card> oldestFirst(Stream<Card> stream) {
        return stream.sorted(Comparator.comparing(Card::createdAt)).toList();
    }
}
