package com.edutrack.application.review;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryAreaRepository;
import com.edutrack.application.support.InMemoryCardRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryReviewLogRepository;
import com.edutrack.application.support.MutableClock;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.CardSide;
import com.edutrack.domain.review.Grade;
import com.edutrack.domain.review.ReviewLog;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate DAY_1 = LocalDate.of(2026, 10, 27);
    private static final int NO_LIMIT = 1000;

    private final MutableClock clock = new MutableClock(NOW);
    private final InMemoryCardRepository cards = new InMemoryCardRepository();
    private final InMemoryReviewLogRepository logs = new InMemoryReviewLogRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryAreaRepository areas = new InMemoryAreaRepository();
    private final ReviewService service = new ReviewService(cards, logs, goals, areas, clock);

    private final Area english = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW));
    private final Area technology = areas.save(Area.create("Technologies", null, AreaIcon.CODE, 1, NOW));
    private final Goal ielts = goal(english.id(), "IELTS 7", "Vocabulary");
    private final Goal java = goal(technology.id(), "Java", "Streams");

    private Goal goal(AreaId areaId, String title, String stepTitle) {
        Goal goal = Goal.create(areaId, title, null, null, 0, NOW);
        goal.addStep(new StepDetails(stepTitle, null, null), StepStatus.NOT_STARTED, NOW);
        return goals.save(goal);
    }

    private static CardDetails card(String front, String back) {
        return new CardDetails(null, front, back, Set.of(), false);
    }

    private static CardDetails reversible(String front, String back) {
        return new CardDetails(null, front, back, Set.of(), true);
    }

    /** Creates the cards one microsecond apart, so creation order is unambiguous. */
    private CardId create(Goal goal, CardDetails details) {
        clock.advance(Duration.ofNanos(1000));
        return service.createCard(goal.id(), details).id();
    }

    private List<String> prompts(List<ReviewItem> queue) {
        return queue.stream().map(ReviewItem::prompt).toList();
    }

    // ---------- cards ----------

    @Test
    void createdCardsAreNewAndListedForTheirGoalAndStep() {
        StepId step = ielts.steps().getFirst().id();
        service.createCard(ielts.id(), new CardDetails(step, "ubiquitous", "found everywhere", Set.of("Adjectives"), true));
        service.createCard(ielts.id(), card("meticulous", "very careful"));
        service.createCard(java.id(), card("map", "transforms"));

        List<CardView> forGoal = service.forGoal(ielts.id());
        List<CardView> forStep = service.forStep(step);

        assertThat(forGoal).extracting(CardView::front).containsExactly("ubiquitous", "meticulous");
        assertThat(forStep).extracting(CardView::front).containsExactly("ubiquitous");
        CardView first = forGoal.getFirst();
        assertThat(first.isNew()).isTrue();
        assertThat(first.nextDue()).isEqualTo(DAY_1);
        assertThat(first.tags()).containsExactly("adjectives");
        assertThat(first.sides()).containsOnlyKeys(CardSide.FORWARD, CardSide.REVERSE);
        assertThat(first.sides().get(CardSide.FORWARD).isNew()).isTrue();
        assertThat(forGoal.get(1).sides()).containsOnlyKeys(CardSide.FORWARD);
    }

    @Test
    void aCardCannotBeCreatedForAMissingGoalOrAStepOfAnotherGoal() {
        StepId foreignStep = java.steps().getFirst().id();

        assertThatThrownBy(() -> service.createCard(GoalId.newId(), card("a", "b"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.createCard(ielts.id(), new CardDetails(foreignStep, "a", "b", Set.of(), false)))
                .isInstanceOf(NotFoundException.class);
        assertThat(cards.size()).isZero();
    }

    @Test
    void invalidCardContentIsRejected() {
        assertThatThrownBy(() -> service.createCard(ielts.id(), card(" ", "b"))).isInstanceOf(DomainValidationException.class);
        assertThat(cards.size()).isZero();
    }

    @Test
    void updateCardChangesTheContentAndTurnsReverseOnAndOff() {
        CardId id = create(ielts, card("a", "b"));

        CardView withReverse = service.updateCard(id, reversible("a2", "b2"));
        CardView withoutReverse = service.updateCard(id, card("a2", "b2"));

        assertThat(withReverse.front()).isEqualTo("a2");
        assertThat(withReverse.sides()).containsOnlyKeys(CardSide.FORWARD, CardSide.REVERSE);
        assertThat(withoutReverse.sides()).containsOnlyKeys(CardSide.FORWARD);
    }

    @Test
    void updateCardChecksTheStepAgainstTheCardsGoal() {
        CardId id = create(ielts, card("a", "b"));
        StepId foreignStep = java.steps().getFirst().id();

        assertThatThrownBy(() -> service.updateCard(id, new CardDetails(foreignStep, "a", "b", Set.of(), false)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.updateCard(CardId.newId(), card("a", "b"))).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteCardRemovesIt() {
        CardId id = create(ielts, card("a", "b"));

        service.deleteCard(id);

        assertThat(service.forGoal(ielts.id())).isEmpty();
    }

    // ---------- queue ----------

    @Test
    void newCardsFillTheQueueInCreationOrderForwardBeforeReverse() {
        create(ielts, reversible("one", "uno"));
        create(ielts, card("two", "dos"));

        List<ReviewItem> queue = service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT);

        assertThat(prompts(queue)).containsExactly("one", "uno", "two");
        assertThat(queue).extracting(ReviewItem::side).containsExactly(CardSide.FORWARD, CardSide.REVERSE, CardSide.FORWARD);
        assertThat(queue).allMatch(ReviewItem::isNew);
    }

    @Test
    void theReverseSideSwapsPromptAndAnswer() {
        create(ielts, reversible("la casa", "the house"));

        List<ReviewItem> queue = service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT);

        assertThat(queue.get(0).prompt()).isEqualTo("la casa");
        assertThat(queue.get(0).answer()).isEqualTo("the house");
        assertThat(queue.get(1).prompt()).isEqualTo("the house");
        assertThat(queue.get(1).answer()).isEqualTo("la casa");
    }

    @Test
    void itemsCarryTheGoalAndAreaNames() {
        create(ielts, card("a", "b"));
        create(java, card("c", "d"));

        List<ReviewItem> queue = service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT);

        assertThat(queue).extracting(ReviewItem::goalTitle).containsExactly("IELTS 7", "Java");
        assertThat(queue).extracting(ReviewItem::areaName).containsExactly("English", "Technologies");
    }

    @Test
    void itemsCarryTheGoalId() {
        create(ielts, card("a", "b"));

        assertThat(service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT)).extracting(ReviewItem::goalId)
                .containsExactly(ielts.id());
    }

    @Test
    void previewIntervalsShowWhatEachGradeWouldGiveWithoutMovingTheSchedule() {
        CardId id = create(ielts, card("a", "b"));

        Map<Grade, Integer> fresh = service.previewIntervals(id, CardSide.FORWARD);
        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);
        Map<Grade, Integer> afterOnePass = service.previewIntervals(id, CardSide.FORWARD);

        assertThat(fresh).containsEntry(Grade.AGAIN, 0).containsEntry(Grade.HARD, 1)
                .containsEntry(Grade.GOOD, 1).containsEntry(Grade.EASY, 1);
        assertThat(afterOnePass).containsEntry(Grade.AGAIN, 0).containsEntry(Grade.GOOD, 6);
        assertThat(service.previewIntervals(id, CardSide.FORWARD)).isEqualTo(afterOnePass);
        assertThat(service.forGoal(ielts.id()).getFirst().sides().get(CardSide.FORWARD).repetitions()).isEqualTo(1);
    }

    @Test
    void previewIntervalsOfAMissingCardOrSideAreRejected() {
        CardId id = create(ielts, card("a", "b"));

        assertThatThrownBy(() -> service.previewIntervals(CardId.newId(), CardSide.FORWARD))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.previewIntervals(id, CardSide.REVERSE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nextDueIsTheEarliestFutureDayOfAStudiedSideWithinTheScope() {
        CardId english1 = create(ielts, card("a", "b"));
        CardId english2 = create(ielts, card("c", "d"));
        CardId tech = create(java, card("e", "f"));
        create(ielts, card("never studied", "x"));
        service.grade(english1, CardSide.FORWARD, Grade.GOOD, 3);   // tomorrow
        service.grade(english1, CardSide.FORWARD, Grade.GOOD, 3);   // 6 days
        service.grade(english2, CardSide.FORWARD, Grade.GOOD, 3);   // tomorrow
        service.grade(tech, CardSide.FORWARD, Grade.GOOD, 3);

        assertThat(service.nextDue(ReviewScope.ALL)).contains(DAY_1.plusDays(1));
        assertThat(service.nextDue(ReviewScope.ofGoal(ielts.id()))).contains(DAY_1.plusDays(1));
    }

    @Test
    void nextDueIsEmptyWithoutStudiedCardsOrWhenEverythingIsDue() {
        CardId id = create(ielts, card("a", "b"));

        assertThat(service.nextDue(ReviewScope.ALL)).isEmpty();

        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);
        clock.advanceDays(3);

        assertThat(service.nextDue(ReviewScope.ALL)).isEmpty();
    }

    @Test
    void nextDueIgnoresCardsOutsideTheScope() {
        CardId id = create(java, card("a", "b"));
        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);

        assertThat(service.nextDue(ReviewScope.ofGoal(ielts.id()))).isEmpty();
        assertThat(service.nextDue(ReviewScope.ofArea(technology.id()))).contains(DAY_1.plusDays(1));
    }

    @Test
    void dueCardsComeBeforeNewOnesAndTieBreakByCreationOrder() {
        CardId older = create(ielts, card("older", "x"));
        CardId newer = create(ielts, card("newer", "x"));
        service.grade(newer, CardSide.FORWARD, Grade.GOOD, 3);
        service.grade(older, CardSide.FORWARD, Grade.GOOD, 3);        // both due the next day
        create(ielts, card("fresh", "x"));
        clock.advanceDays(5);

        List<ReviewItem> queue = service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT);

        assertThat(prompts(queue)).containsExactly("older", "newer", "fresh");
        assertThat(queue).extracting(ReviewItem::isNew).containsExactly(false, false, true);
    }

    @Test
    void theMostOverdueSideIsShownFirst() {
        CardId early = create(ielts, card("early", "x"));
        service.grade(early, CardSide.FORWARD, Grade.GOOD, 3);        // due 28 Oct
        clock.advanceDays(3);
        CardId late = create(ielts, card("late", "x"));
        service.grade(late, CardSide.FORWARD, Grade.GOOD, 3);         // due 31 Oct
        clock.advanceDays(10);

        List<ReviewItem> queue = service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT);

        assertThat(prompts(queue)).containsExactly("early", "late");
    }

    @Test
    void cardsNotYetDueAreLeftOut() {
        CardId id = create(ielts, card("a", "b"));
        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);

        assertThat(service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT)).isEmpty();
        clock.advanceDays(1);
        assertThat(service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT)).hasSize(1);
    }

    @Test
    void aForgottenCardStaysInTheQueueForTheSameDay() {
        CardId id = create(ielts, card("a", "b"));

        service.grade(id, CardSide.FORWARD, Grade.AGAIN, 5);

        List<ReviewItem> queue = service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT);
        assertThat(queue).hasSize(1);
        assertThat(queue.getFirst().isNew()).isFalse();
    }

    @Test
    void theDailyNewLimitCapsNewCardsAndCountsThoseAlreadyIntroducedToday() {
        CardId first = create(ielts, card("a", "x"));
        create(ielts, card("b", "x"));
        create(ielts, card("c", "x"));
        create(ielts, card("d", "x"));

        assertThat(prompts(service.queue(ReviewScope.ALL, 2, NO_LIMIT))).containsExactly("a", "b");

        service.grade(first, CardSide.FORWARD, Grade.GOOD, 3);

        assertThat(prompts(service.queue(ReviewScope.ALL, 2, NO_LIMIT))).containsExactly("b");
        clock.advanceDays(1);
        assertThat(prompts(service.queue(ReviewScope.ALL, 2, NO_LIMIT))).containsExactly("a", "b", "c");
    }

    @Test
    void theDailyReviewLimitCapsDueCardsAndCountsReviewsAlreadyDoneToday() {
        CardId a = create(ielts, card("a", "x"));
        CardId b = create(ielts, card("b", "x"));
        CardId c = create(ielts, card("c", "x"));
        for (CardId id : List.of(a, b, c)) {
            service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);
        }
        clock.advanceDays(2);

        assertThat(prompts(service.queue(ReviewScope.ALL, NO_LIMIT, 2))).containsExactly("a", "b");

        service.grade(a, CardSide.FORWARD, Grade.GOOD, 3);

        assertThat(prompts(service.queue(ReviewScope.ALL, NO_LIMIT, 2))).containsExactly("b");
        service.grade(b, CardSide.FORWARD, Grade.GOOD, 3);
        assertThat(service.queue(ReviewScope.ALL, NO_LIMIT, 2)).isEmpty();
    }

    @Test
    void newAndReviewLimitsAreIndependent() {
        CardId learned = create(ielts, card("learned", "x"));
        service.grade(learned, CardSide.FORWARD, Grade.GOOD, 3);
        clock.advanceDays(1);
        create(ielts, card("fresh", "x"));

        assertThat(prompts(service.queue(ReviewScope.ALL, 1, 1))).containsExactly("learned", "fresh");
        assertThat(prompts(service.queue(ReviewScope.ALL, 0, 1))).containsExactly("learned");
        assertThat(prompts(service.queue(ReviewScope.ALL, 1, 0))).containsExactly("fresh");
    }

    @Test
    void scopeRestrictsTheQueueToAnAreaOrAGoal() {
        create(ielts, card("english", "x"));
        create(java, card("java", "x"));

        assertThat(prompts(service.queue(ReviewScope.ofArea(english.id()), NO_LIMIT, NO_LIMIT))).containsExactly("english");
        assertThat(prompts(service.queue(ReviewScope.ofArea(technology.id()), NO_LIMIT, NO_LIMIT))).containsExactly("java");
        assertThat(prompts(service.queue(ReviewScope.ofGoal(java.id()), NO_LIMIT, NO_LIMIT))).containsExactly("java");
        assertThat(service.queue(new ReviewScope(technology.id(), ielts.id()), NO_LIMIT, NO_LIMIT)).isEmpty();
        assertThat(prompts(service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT))).containsExactly("english", "java");
    }

    @Test
    void dueCountMatchesTheQueueWithinTheLimits() {
        create(ielts, card("a", "x"));
        create(ielts, card("b", "x"));
        create(ielts, card("c", "x"));

        assertThat(service.dueCount(ReviewScope.ALL, 2, NO_LIMIT)).isEqualTo(2);
        assertThat(service.dueCount(ReviewScope.ALL, NO_LIMIT, NO_LIMIT)).isEqualTo(3);
        assertThat(service.dueCount(ReviewScope.ofGoal(java.id()), NO_LIMIT, NO_LIMIT)).isZero();
    }

    // ---------- grading ----------

    @Test
    void gradingFollowsSm2AndReportsTheNextDue() {
        CardId id = create(ielts, card("a", "b"));

        GradeResult first = service.grade(id, CardSide.FORWARD, Grade.GOOD, 7);
        clock.advanceDays(1);
        GradeResult second = service.grade(id, CardSide.FORWARD, Grade.GOOD, 7);
        clock.advanceDays(6);
        GradeResult third = service.grade(id, CardSide.FORWARD, Grade.GOOD, 7);

        assertThat(first).isEqualTo(new GradeResult(DAY_1.plusDays(1), 1));
        assertThat(second).isEqualTo(new GradeResult(DAY_1.plusDays(7), 6));
        assertThat(third).isEqualTo(new GradeResult(DAY_1.plusDays(22), 15));
        assertThat(service.forGoal(ielts.id()).getFirst().sides().get(CardSide.FORWARD).repetitions()).isEqualTo(3);
    }

    @Test
    void anAgainAnswerIsDueTodayWithAZeroInterval() {
        CardId id = create(ielts, card("a", "b"));

        assertThat(service.grade(id, CardSide.FORWARD, Grade.AGAIN, 7)).isEqualTo(new GradeResult(DAY_1, 0));
    }

    @Test
    void gradingAppendsToTheReviewHistory() {
        CardId id = create(ielts, reversible("a", "b"));

        service.grade(id, CardSide.REVERSE, Grade.HARD, 12);
        service.grade(id, CardSide.REVERSE, Grade.GOOD, 4);

        List<ReviewLog> history = logs.all();
        assertThat(history).hasSize(2);
        assertThat(history.get(0).cardId()).isEqualTo(id);
        assertThat(history.get(0).side()).isEqualTo(CardSide.REVERSE);
        assertThat(history.get(0).grade()).isEqualTo(Grade.HARD);
        assertThat(history.get(0).seconds()).isEqualTo(12);
        assertThat(history.get(0).wasNew()).isTrue();
        assertThat(history.get(1).wasNew()).isFalse();
    }

    @Test
    void gradingOneSideLeavesTheOtherNew() {
        CardId id = create(ielts, reversible("a", "b"));

        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);

        CardView view = service.forGoal(ielts.id()).getFirst();
        assertThat(view.sides().get(CardSide.FORWARD).isNew()).isFalse();
        assertThat(view.sides().get(CardSide.REVERSE).isNew()).isTrue();
        assertThat(view.isNew()).isFalse();
        assertThat(view.nextDue()).isEqualTo(DAY_1);
    }

    @Test
    void gradingAMissingCardOrSideFails() {
        CardId id = create(ielts, card("a", "b"));

        assertThatThrownBy(() -> service.grade(CardId.newId(), CardSide.FORWARD, Grade.GOOD, 1))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.grade(id, CardSide.REVERSE, Grade.GOOD, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(logs.all()).isEmpty();
    }

    @Test
    void anInvalidGradeChangesNothing() {
        CardId id = create(ielts, card("a", "b"));

        assertThatThrownBy(() -> service.grade(id, CardSide.FORWARD, Grade.GOOD, -5))
                .isInstanceOf(DomainValidationException.class);

        assertThat(logs.all()).isEmpty();
        assertThat(service.forGoal(ielts.id()).getFirst().isNew()).isTrue();
    }

    // ---------- import ----------

    private ImportResult importText(String text) {
        return service.importDelimited(ielts.id(), null, text);
    }

    private List<String> importedFronts() {
        return service.forGoal(ielts.id()).stream().map(CardView::front).toList();
    }

    @Test
    void importsTabSeparatedLines() {
        ImportResult result = importText("hello\thola\ngoodbye\tadiós");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).isEmpty();
        assertThat(service.forGoal(ielts.id())).extracting(CardView::front).containsExactly("hello", "goodbye");
        assertThat(service.forGoal(ielts.id())).extracting(CardView::back).containsExactly("hola", "adiós");
    }

    @Test
    void importsSemicolonAndCommaSeparatedLines() {
        ImportResult result = importText("cat;gato\ndog, perro\nbird ; pájaro");

        assertThat(result.imported()).isEqualTo(3);
        assertThat(service.forGoal(ielts.id())).extracting(CardView::front).containsExactly("cat", "dog", "bird");
        assertThat(service.forGoal(ielts.id())).extracting(CardView::back).containsExactly("gato", "perro", "pájaro");
    }

    @Test
    void theTabWinsOverSemicolonsAndTheSemicolonOverCommas() {
        importText("a;b\tc,d\ne,f;g");

        assertThat(service.forGoal(ielts.id())).extracting(CardView::front).containsExactly("a;b", "e,f");
        assertThat(service.forGoal(ielts.id())).extracting(CardView::back).containsExactly("c,d", "g");
    }

    @Test
    void splitsOnlyOnTheFirstSeparator() {
        importText("run,to move fast, quickly");

        CardView card = service.forGoal(ielts.id()).getFirst();
        assertThat(card.front()).isEqualTo("run");
        assertThat(card.back()).isEqualTo("to move fast, quickly");
    }

    @Test
    void quotedSidesMayContainSeparatorsAndLoseTheirQuotes() {
        importText("\"to be, or not\",\"ser, o no ser\"\n\"say \"\"hi\"\"\";\"saluda\"");

        List<CardView> cards = service.forGoal(ielts.id());
        assertThat(cards).extracting(CardView::front).containsExactly("to be, or not", "say \"hi\"");
        assertThat(cards).extracting(CardView::back).containsExactly("ser, o no ser", "saluda");
    }

    @Test
    void anUnbalancedQuoteInTheFrontIsTakenLiterally() {
        importText("a 6\" ruler;una regla");

        assertThat(service.forGoal(ielts.id()).getFirst().front()).isEqualTo("a 6\" ruler");
    }

    @Test
    void blankLinesAndCommentsAreIgnoredSilently() {
        ImportResult result = importText("# vocabulary\n\n   \nhello;hola\n#hidden;line\n");

        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.skipped()).isEmpty();
    }

    @Test
    void linesWithoutBothSidesAreSkippedWithTheirLineNumber() {
        ImportResult result = importText("good;bueno\nnoseparator\n;onlyback\nonlyfront;\n\"quoted only\"\nfine;ok");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).hasSize(4);
        assertThat(result.skipped().get(0)).startsWith("line 2:");
        assertThat(result.skipped().get(1)).startsWith("line 3:");
        assertThat(result.skipped().get(2)).startsWith("line 4:");
        assertThat(result.skipped().get(3)).startsWith("line 5:");
        assertThat(importedFronts()).containsExactly("good", "fine");
    }

    @Test
    void handlesWindowsLineEndings() {
        ImportResult result = importText("a;b\r\nc;d\r\n");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(importedFronts()).containsExactly("a", "c");
    }

    @Test
    void aLineThatBreaksACardRuleIsSkippedAndTheRestStillImported() {
        ImportResult result = importText("ok;fine\ntoolong;" + "x".repeat(4001) + "\nlast;one");

        assertThat(result.imported()).isEqualTo(2);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().getFirst()).startsWith("line 2:").contains("4000");
    }

    @Test
    void importedCardsAreNewKeepTheTextOrderAndCanBeFiledUnderAStep() {
        StepId step = ielts.steps().getFirst().id();

        ImportResult result = service.importDelimited(ielts.id(), step, "one;1\ntwo;2\nthree;3");

        assertThat(result.imported()).isEqualTo(3);
        assertThat(service.forStep(step)).extracting(CardView::front).containsExactly("one", "two", "three");
        assertThat(service.forStep(step)).allMatch(CardView::isNew);
        assertThat(prompts(service.queue(ReviewScope.ALL, NO_LIMIT, NO_LIMIT))).containsExactly("one", "two", "three");
    }

    @Test
    void importingIntoAMissingGoalOrForeignStepFails() {
        StepId foreignStep = java.steps().getFirst().id();

        assertThatThrownBy(() -> service.importDelimited(GoalId.newId(), null, "a;b")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.importDelimited(ielts.id(), foreignStep, "a;b")).isInstanceOf(NotFoundException.class);
        assertThat(cards.size()).isZero();
    }

    @Test
    void importingEmptyTextImportsNothing() {
        assertThat(importText("")).isEqualTo(new ImportResult(0, List.of()));
        assertThat(service.importDelimited(ielts.id(), null, null)).isEqualTo(new ImportResult(0, List.of()));
    }

    // ---------- study days ----------

    @Test
    void reviewsMakeStudyDays() {
        ReviewStudyDays source = new ReviewStudyDays(logs, clock);
        CardId id = create(ielts, card("a", "b"));
        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);
        clock.advanceDays(3);
        service.grade(id, CardSide.FORWARD, Grade.GOOD, 3);

        assertThat(source.studyDays(DAY_1.minusDays(5), DAY_1.plusDays(10)))
                .containsExactlyInAnyOrder(DAY_1, DAY_1.plusDays(3));
        assertThat(source.studyDays(DAY_1.plusDays(1), DAY_1.plusDays(2))).isEmpty();
    }
}
