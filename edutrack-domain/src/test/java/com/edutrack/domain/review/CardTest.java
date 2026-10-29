package com.edutrack.domain.review;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CardTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final GoalId goalId = GoalId.newId();

    private static CardDetails details(String front, String back, boolean reversible, String... tags) {
        return new CardDetails(null, front, back, new LinkedHashSet<>(Set.of(tags)), reversible);
    }

    private Card card(boolean reversible) {
        return Card.create(goalId, details("la casa", "the house", reversible), TODAY, NOW);
    }

    // ---------- creation ----------

    @Test
    void aNewCardHasANewForwardScheduleDueToday() {
        Card card = card(false);

        assertThat(card.front()).isEqualTo("la casa");
        assertThat(card.back()).isEqualTo("the house");
        assertThat(card.goalId()).isEqualTo(goalId);
        assertThat(card.createdAt()).isEqualTo(NOW);
        assertThat(card.reversible()).isFalse();
        assertThat(card.schedules()).containsOnlyKeys(CardSide.FORWARD);
        assertThat(card.schedule(CardSide.FORWARD)).isEqualTo(CardSchedule.newCard(TODAY));
        assertThat(card.schedule(CardSide.FORWARD).isNew()).isTrue();
    }

    @Test
    void aReversibleCardHasAnIndependentReverseSchedule() {
        Card card = card(true);

        assertThat(card.schedules()).containsOnlyKeys(CardSide.FORWARD, CardSide.REVERSE);
        assertThat(card.schedule(CardSide.REVERSE).isNew()).isTrue();
        assertThat(card.hasSide(CardSide.REVERSE)).isTrue();
    }

    @Test
    void frontAndBackAreRequiredStrippedAndAtMost4000Characters() {
        Card card = Card.create(goalId, details("  front  ", "\nback\n", false), TODAY, NOW);
        DomainValidationException noFront = catchThrowableOfType(DomainValidationException.class,
                () -> Card.create(goalId, details(" ", "b", false), TODAY, NOW));
        DomainValidationException noBack = catchThrowableOfType(DomainValidationException.class,
                () -> Card.create(goalId, details("f", null, false), TODAY, NOW));
        DomainValidationException tooLong = catchThrowableOfType(DomainValidationException.class,
                () -> Card.create(goalId, details("f", "x".repeat(4001), false), TODAY, NOW));

        assertThat(card.front()).isEqualTo("front");
        assertThat(card.back()).isEqualTo("back");
        assertThat(noFront.code()).isEqualTo("required");
        assertThat(noFront.args()).containsExactly("Front");
        assertThat(noBack.args()).containsExactly("Back");
        assertThat(tooLong.code()).isEqualTo("too-long");
        assertThat(tooLong.args()).containsExactly("Back", 4000);
    }

    // ---------- tags ----------

    @Test
    void tagsAreStrippedLowerCasedAndDeduplicated() {
        Card card = Card.create(goalId, details("f", "b", false, " Verbs ", "VERBS", "Food", " "), TODAY, NOW);

        assertThat(card.tags()).containsExactly("food", "verbs");
    }

    @Test
    void noTagsMeansAnEmptySet() {
        Card card = Card.create(goalId, new CardDetails(null, "f", "b", null, false), TODAY, NOW);

        assertThat(card.tags()).isEmpty();
    }

    @Test
    void aTagIsAtMost40Characters() {
        DomainValidationException error = catchThrowableOfType(DomainValidationException.class,
                () -> Card.create(goalId, details("f", "b", false, "x".repeat(41)), TODAY, NOW));

        assertThat(error.code()).isEqualTo("too-long");
        assertThat(error.args()).containsExactly("Tag", 40);
    }

    @Test
    void aCardHasAtMost20Tags() {
        Set<String> twenty = IntStream.range(0, 20).mapToObj(i -> "tag" + i).collect(Collectors.toSet());
        Set<String> twentyOne = new HashSet<>(twenty);
        twentyOne.add("one-more");

        Card ok = Card.create(goalId, new CardDetails(null, "f", "b", twenty, false), TODAY, NOW);
        DomainValidationException error = catchThrowableOfType(DomainValidationException.class,
                () -> Card.create(goalId, new CardDetails(null, "f", "b", twentyOne, false), TODAY, NOW));

        assertThat(ok.tags()).hasSize(20);
        assertThat(error.code()).isEqualTo("too-large");
        assertThat(error.args()).containsExactly("Tags", 20);
    }

    @Test
    void tagsDifferingOnlyByCaseCountOnce() {
        Set<String> tags = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            tags.add("tag" + i);
        }
        tags.add("TAG0");

        Card card = Card.create(goalId, new CardDetails(null, "f", "b", tags, false), TODAY, NOW);

        assertThat(card.tags()).hasSize(20);
    }

    // ---------- update ----------

    @Test
    void updateChangesTheContentAndKeepsTheSchedule() {
        Card card = card(false);
        card.review(CardSide.FORWARD, Grade.GOOD, 5, TODAY, NOW);
        CardSchedule before = card.schedule(CardSide.FORWARD);
        StepId step = StepId.newId();

        card.update(new CardDetails(step, "el perro", "the dog", Set.of("Animals"), false), TODAY.plusDays(3));

        assertThat(card.front()).isEqualTo("el perro");
        assertThat(card.stepId()).isEqualTo(step);
        assertThat(card.tags()).containsExactly("animals");
        assertThat(card.schedule(CardSide.FORWARD)).isEqualTo(before);
    }

    @Test
    void aRejectedUpdateChangesNothing() {
        Card card = card(false);

        assertThatThrownBy(() -> card.update(details("new front", " ", true), TODAY))
                .isInstanceOf(DomainValidationException.class);

        assertThat(card.front()).isEqualTo("la casa");
        assertThat(card.reversible()).isFalse();
        assertThat(card.schedules()).containsOnlyKeys(CardSide.FORWARD);
    }

    @Test
    void turningReversibleOnAddsANewReverseScheduleDueFromThatDay() {
        Card card = card(false);
        LocalDate later = TODAY.plusDays(10);

        card.update(details("la casa", "the house", true), later);

        assertThat(card.schedule(CardSide.REVERSE)).isEqualTo(CardSchedule.newCard(later));
        assertThat(card.schedule(CardSide.FORWARD)).isEqualTo(CardSchedule.newCard(TODAY));
    }

    @Test
    void turningReversibleOffRemovesTheReverseSchedule() {
        Card card = card(true);

        card.update(details("la casa", "the house", false), TODAY);

        assertThat(card.schedules()).containsOnlyKeys(CardSide.FORWARD);
        assertThat(card.hasSide(CardSide.REVERSE)).isFalse();
        assertThatThrownBy(() -> card.schedule(CardSide.REVERSE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepingTheCardReversibleKeepsTheReverseProgress() {
        Card card = card(true);
        card.review(CardSide.REVERSE, Grade.GOOD, 4, TODAY, NOW);
        CardSchedule reverse = card.schedule(CardSide.REVERSE);

        card.update(details("la casa!", "the house", true), TODAY.plusDays(1));

        assertThat(card.schedule(CardSide.REVERSE)).isEqualTo(reverse);
    }

    // ---------- review ----------

    @Test
    void reviewingUsesSm2AndReturnsTheLogEntry() {
        Card card = card(false);

        ReviewLog log = card.review(CardSide.FORWARD, Grade.GOOD, 12, TODAY, NOW);

        assertThat(card.schedule(CardSide.FORWARD).dueOn()).isEqualTo(TODAY.plusDays(1));
        assertThat(card.schedule(CardSide.FORWARD).intervalDays()).isEqualTo(1);
        assertThat(log.cardId()).isEqualTo(card.id());
        assertThat(log.side()).isEqualTo(CardSide.FORWARD);
        assertThat(log.grade()).isEqualTo(Grade.GOOD);
        assertThat(log.seconds()).isEqualTo(12);
        assertThat(log.reviewedAt()).isEqualTo(NOW);
        assertThat(log.wasNew()).isTrue();
        assertThat(log.id()).isNotNull();
    }

    @Test
    void theLogRemembersWhetherTheSideWasNew() {
        Card card = card(false);

        ReviewLog first = card.review(CardSide.FORWARD, Grade.GOOD, 5, TODAY, NOW);
        ReviewLog second = card.review(CardSide.FORWARD, Grade.GOOD, 5, TODAY.plusDays(1), NOW.plusSeconds(86400));

        assertThat(first.wasNew()).isTrue();
        assertThat(second.wasNew()).isFalse();
        assertThat(card.schedule(CardSide.FORWARD).intervalDays()).isEqualTo(6);
    }

    @Test
    void eachSideIsScheduledOnItsOwn() {
        Card card = card(true);

        card.review(CardSide.FORWARD, Grade.EASY, 3, TODAY, NOW);

        assertThat(card.schedule(CardSide.FORWARD).ease()).isEqualTo(new BigDecimal("2.60"));
        assertThat(card.schedule(CardSide.REVERSE)).isEqualTo(CardSchedule.newCard(TODAY));
    }

    @Test
    void reviewingAMissingSideIsAMistake() {
        Card card = card(false);

        assertThatThrownBy(() -> card.review(CardSide.REVERSE, Grade.GOOD, 1, TODAY, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secondsCannotBeNegative() {
        Card card = card(false);

        DomainValidationException error = catchThrowableOfType(DomainValidationException.class,
                () -> card.review(CardSide.FORWARD, Grade.GOOD, -1, TODAY, NOW));

        assertThat(error.code()).isEqualTo("negative");
        assertThat(card.schedule(CardSide.FORWARD).isNew()).isTrue();
    }

    // ---------- restore ----------

    @Test
    void restoreKeepsTheStoredSchedules() {
        CardId id = CardId.newId();
        CardSchedule forward = new CardSchedule(new BigDecimal("2.36"), 6, 2, 1, TODAY.plusDays(6), TODAY);
        Map<CardSide, CardSchedule> schedules = new EnumMap<>(CardSide.class);
        schedules.put(CardSide.FORWARD, forward);

        Card card = Card.restore(id, goalId, details("f", "b", false, "x"), NOW, schedules);

        assertThat(card.id()).isEqualTo(id);
        assertThat(card.schedule(CardSide.FORWARD)).isEqualTo(forward);
        assertThat(card.tags()).containsExactly("x");
    }

    @Test
    void restoreRejectsSchedulesThatDisagreeWithReversible() {
        Map<CardSide, CardSchedule> onlyForward = Map.of(CardSide.FORWARD, CardSchedule.newCard(TODAY));
        Map<CardSide, CardSchedule> both = Map.of(CardSide.FORWARD, CardSchedule.newCard(TODAY),
                CardSide.REVERSE, CardSchedule.newCard(TODAY));
        CardId id = CardId.newId();

        assertThatThrownBy(() -> Card.restore(id, goalId, details("f", "b", true), NOW, onlyForward))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Card.restore(id, goalId, details("f", "b", false), NOW, both))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Card.restore(id, goalId, details("f", "b", false), NOW, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
