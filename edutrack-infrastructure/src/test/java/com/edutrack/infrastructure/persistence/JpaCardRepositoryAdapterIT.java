package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.review.Card;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardRepository;
import com.edutrack.domain.review.CardSchedule;
import com.edutrack.domain.review.CardSide;
import com.edutrack.domain.review.Grade;
import com.edutrack.domain.review.ReviewLog;
import com.edutrack.domain.review.ReviewLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static com.edutrack.infrastructure.persistence.GoalFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaCardRepositoryAdapter.class,
        JpaReviewLogRepositoryAdapter.class})
class JpaCardRepositoryAdapterIT {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 3, 1);

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private CardRepository cards;

    @Autowired
    private ReviewLogRepository logs;

    @Autowired
    private TestEntityManager entityManager;

    private Goal goalWithSteps(int steps) {
        Goal goal = GoalFixtures.saveGoalWithSteps(areas, goals, steps);
        entityManager.flush();
        return goal;
    }

    private Card card(Goal goal, StepId step, String front, boolean reversible, Instant createdAt) {
        return Card.create(goal.id(), new CardDetails(step, front, "back of " + front, Set.of(), reversible), DAY_1, createdAt);
    }

    private Card card(Goal goal, String front, Instant createdAt) {
        return card(goal, null, front, false, createdAt);
    }

    @Test
    void roundTripsACardWithTagsStepAndBothSides() {
        Goal goal = goalWithSteps(2);
        StepId step = goal.steps().get(1).id();
        Card card = Card.create(goal.id(), new CardDetails(step, "**la casa**\n\nmultiline", "the house, `home`",
                Set.of("Nouns", "a1", "with space"), true), DAY_1, NOW);
        card.review(CardSide.FORWARD, Grade.GOOD, 5, DAY_1, NOW);

        Card saved = cards.save(card);
        flushAndClear();
        Card loaded = cards.findById(card.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(card);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(card);
        assertThat(loaded.tags()).containsExactly("a1", "nouns", "with space");
        assertThat(loaded.stepId()).isEqualTo(step);
        assertThat(loaded.schedule(CardSide.FORWARD).dueOn()).isEqualTo(DAY_1.plusDays(1));
        assertThat(loaded.schedule(CardSide.FORWARD).lastReviewedOn()).isEqualTo(DAY_1);
        assertThat(loaded.schedule(CardSide.REVERSE).isNew()).isTrue();
        assertThat(loaded.schedule(CardSide.REVERSE).lastReviewedOn()).isNull();
    }

    @Test
    void roundTripsACardWithoutTagsOrStep() {
        Goal goal = goalWithSteps(1);
        Card card = card(goal, "plain", NOW);

        cards.save(card);
        flushAndClear();
        Card loaded = cards.findById(card.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(card);
        assertThat(loaded.tags()).isEmpty();
        assertThat(loaded.stepId()).isNull();
        assertThat(loaded.schedules()).containsOnlyKeys(CardSide.FORWARD);
    }

    @Test
    void scheduleValuesSurviveExactly() {
        Goal goal = goalWithSteps(1);
        Card card = card(goal, "a", NOW);
        card.review(CardSide.FORWARD, Grade.GOOD, 1, DAY_1, NOW);
        card.review(CardSide.FORWARD, Grade.HARD, 1, DAY_1.plusDays(1), NOW);
        card.review(CardSide.FORWARD, Grade.AGAIN, 1, DAY_1.plusDays(2), NOW);
        CardSchedule expected = card.schedule(CardSide.FORWARD);

        cards.save(card);
        flushAndClear();

        CardSchedule loaded = cards.findById(card.id()).orElseThrow().schedule(CardSide.FORWARD);
        assertThat(loaded).isEqualTo(expected);
        assertThat(loaded.ease()).isEqualTo(new BigDecimal("1.82"));
        assertThat(loaded.lapses()).isEqualTo(1);
    }

    @Test
    void savingAfterReviewingUpdatesTheScheduleRowInPlace() {
        Goal goal = goalWithSteps(1);
        Card card = cards.save(card(goal, "a", NOW));
        flushAndClear();

        Card reloaded = cards.findById(card.id()).orElseThrow();
        reloaded.review(CardSide.FORWARD, Grade.EASY, 3, DAY_1, NOW);
        cards.save(reloaded);
        flushAndClear();

        CardSchedule schedule = cards.findById(card.id()).orElseThrow().schedule(CardSide.FORWARD);
        assertThat(schedule.ease()).isEqualTo(new BigDecimal("2.60"));
        assertThat(schedule.repetitions()).isEqualTo(1);
        assertThat(count("card_schedule", "card_id", card.id().value())).isEqualTo(1);
    }

    @Test
    void turningReversibleOnAndOffAddsAndRemovesTheReverseRow() {
        Goal goal = goalWithSteps(1);
        Card card = cards.save(card(goal, "a", NOW));
        flushAndClear();

        Card on = cards.findById(card.id()).orElseThrow();
        on.update(new CardDetails(null, "a", "b", Set.of(), true), DAY_1.plusDays(5));
        cards.save(on);
        flushAndClear();
        Card withReverse = cards.findById(card.id()).orElseThrow();

        assertThat(withReverse.reversible()).isTrue();
        assertThat(withReverse.schedule(CardSide.REVERSE)).isEqualTo(CardSchedule.newCard(DAY_1.plusDays(5)));
        assertThat(count("card_schedule", "card_id", card.id().value())).isEqualTo(2);

        withReverse.update(new CardDetails(null, "a", "b", Set.of("x"), false), DAY_1.plusDays(6));
        cards.save(withReverse);
        flushAndClear();
        Card withoutReverse = cards.findById(card.id()).orElseThrow();

        assertThat(withoutReverse.reversible()).isFalse();
        assertThat(withoutReverse.schedules()).containsOnlyKeys(CardSide.FORWARD);
        assertThat(withoutReverse.tags()).containsExactly("x");
        assertThat(count("card_schedule", "card_id", card.id().value())).isEqualTo(1);
    }

    @Test
    void findByGoalAndByStepAreOrderedByCreationAndScoped() {
        Goal goal = goalWithSteps(2);
        Goal other = goalWithSteps(1);
        StepId step = goal.steps().get(0).id();
        cards.save(card(goal, step, "second", false, NOW.plusSeconds(10)));
        cards.save(card(goal, null, "first", false, NOW));
        cards.save(card(goal, step, "third", true, NOW.plusSeconds(20)));
        cards.save(card(other, "elsewhere", NOW));
        flushAndClear();

        assertThat(cards.findByGoal(goal.id())).extracting(Card::front).containsExactly("first", "second", "third");
        assertThat(cards.findByStep(step)).extracting(Card::front).containsExactly("second", "third");
        assertThat(cards.findByStep(goal.steps().get(1).id())).isEmpty();
        assertThat(cards.findByGoal(goal.id()).get(2).schedules()).containsOnlyKeys(CardSide.FORWARD, CardSide.REVERSE);
        assertThat(cards.countByGoal(goal.id())).isEqualTo(3);
        assertThat(cards.countByGoal(other.id())).isEqualTo(1);
    }

    @Test
    void findDueOrNewReturnsCardsWithADueOrNeverStudiedSideInCreationOrder() {
        Goal goal = goalWithSteps(1);
        Card due = card(goal, "due", NOW.plusSeconds(1));
        due.review(CardSide.FORWARD, Grade.GOOD, 1, DAY_1, NOW);                      // due 2 Mar
        Card notYet = card(goal, "not yet", NOW.plusSeconds(2));
        notYet.review(CardSide.FORWARD, Grade.GOOD, 1, DAY_1.plusDays(10), NOW);      // due 12 Mar
        Card brandNew = card(goal, "new", NOW.plusSeconds(3));
        Card halfNew = card(goal, null, "half new", true, NOW.plusSeconds(4));
        halfNew.review(CardSide.FORWARD, Grade.GOOD, 1, DAY_1.plusDays(10), NOW);     // forward not due, reverse new
        for (Card card : new Card[]{halfNew, notYet, brandNew, due}) {
            cards.save(card);
        }
        flushAndClear();

        assertThat(cards.findDueOrNew(DAY_1.plusDays(3))).extracting(Card::front)
                .containsExactly("due", "new", "half new");
        assertThat(cards.findDueOrNew(DAY_1.plusDays(30))).extracting(Card::front)
                .containsExactly("due", "not yet", "new", "half new");
        assertThat(cards.findDueOrNew(DAY_1.plusDays(3))).filteredOn(c -> c.front().equals("half new"))
                .singleElement().satisfies(c -> assertThat(c.schedules()).containsOnlyKeys(CardSide.FORWARD, CardSide.REVERSE));
    }

    @Test
    void aCardWhoseSidesAreAllStudiedAndNotDueIsLeftOut() {
        Goal goal = goalWithSteps(1);
        Card card = card(goal, "studied", NOW);
        card.review(CardSide.FORWARD, Grade.GOOD, 1, DAY_1, NOW);
        cards.save(card);
        flushAndClear();

        assertThat(cards.findDueOrNew(DAY_1)).isEmpty();
        assertThat(cards.findDueOrNew(DAY_1.plusDays(1))).hasSize(1);
    }

    @Test
    void deleteRemovesTheCardItsSchedulesAndItsReviewHistory() {
        Goal goal = goalWithSteps(1);
        Card card = card(goal, null, "a", true, NOW);
        cards.save(card);
        ReviewLog log = card.review(CardSide.FORWARD, Grade.GOOD, 1, DAY_1, NOW);
        cards.save(card);
        logs.append(log);
        flushAndClear();

        cards.delete(card.id());
        cards.delete(card.id());
        flushAndClear();

        assertThat(cards.findById(card.id())).isEmpty();
        assertThat(count("card_schedule", "card_id", card.id().value())).isZero();
        assertThat(count("review_log", "card_id", card.id().value())).isZero();
    }

    @Test
    void deletingTheGoalDeletesItsCards() {
        Goal goal = goalWithSteps(1);
        Card card = cards.save(card(goal, "a", NOW));
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(cards.findById(card.id())).isEmpty();
        assertThat(count("card_schedule", "card_id", card.id().value())).isZero();
    }

    @Test
    void removingTheStepKeepsTheCardWithoutAStep() {
        Goal goal = goalWithSteps(2);
        StepId removed = goal.steps().get(0).id();
        Card card = cards.save(card(goal, removed, "a", false, NOW));
        flushAndClear();

        Goal reloaded = goals.findById(goal.id()).orElseThrow();
        reloaded.removeStep(removed);
        goals.save(reloaded);
        flushAndClear();

        Card kept = cards.findById(card.id()).orElseThrow();
        assertThat(kept.stepId()).isNull();
        assertThat(kept.front()).isEqualTo("a");
    }

    @Test
    void aCardCannotBelongToAMissingGoal() {
        Card orphan = Card.create(GoalId.newId(),
                new CardDetails(null, "a", "b", Set.of(), false), DAY_1, NOW);

        assertThatThrownBy(() -> cards.save(orphan)).hasStackTraceContaining("foreign key");
    }

    @Test
    void findByIdOfAMissingCardIsEmpty() {
        assertThat(cards.findById(CardId.newId())).isEmpty();
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long count(String table, String column, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery("select count(*) from " + table + " where " + column + " = :id")
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
