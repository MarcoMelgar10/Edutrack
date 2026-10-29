package com.edutrack.application.review;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.RoadmapStep;
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
import com.edutrack.domain.review.Sm2Scheduler;
import com.edutrack.domain.shared.DomainValidationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Use cases for flashcards: managing the cards of a goal and running spaced-repetition reviews.
 *
 * <p>The review queue shows what is due first, then new cards, each part capped by a daily limit
 * that counts what was already studied today. Grading moves the side's SM-2 schedule and appends
 * to the review history, which the daily limits and the study streak are read from.
 */
public class ReviewService {

    private final CardRepository cards;
    private final ReviewLogRepository logs;
    private final GoalRepository goals;
    private final AreaRepository areas;
    private final Clock clock;

    public ReviewService(CardRepository cards, ReviewLogRepository logs, GoalRepository goals, AreaRepository areas,
                         Clock clock) {
        this.cards = cards;
        this.logs = logs;
        this.goals = goals;
        this.areas = areas;
        this.clock = clock;
    }

    // ---------- cards ----------

    /** The goal's cards, oldest first. */
    public List<CardView> forGoal(GoalId goalId) {
        return cards.findByGoal(goalId).stream().map(CardView::of).toList();
    }

    /** The cards filed under a roadmap step, oldest first. */
    public List<CardView> forStep(StepId stepId) {
        return cards.findByStep(stepId).stream().map(CardView::of).toList();
    }

    public CardView createCard(GoalId goalId, CardDetails details) {
        Goal goal = goal(goalId);
        ensureStepBelongsTo(goal, details.stepId());
        Card card = Card.create(goalId, details, LocalDate.now(clock), clock.instant());
        return CardView.of(cards.save(card));
    }

    public CardView updateCard(CardId id, CardDetails details) {
        Card card = load(id);
        ensureStepBelongsTo(goal(card.goalId()), details.stepId());
        card.update(details, LocalDate.now(clock));
        return CardView.of(cards.save(card));
    }

    public void deleteCard(CardId id) {
        cards.delete(id);
    }

    // ---------- reviewing ----------

    /**
     * What to study now within {@code scope}: the sides due today or earlier, most overdue first,
     * then sides never studied, in card creation order (forward before reverse). The review part
     * stops when {@code dailyReviewLimit} reviews were done today, the new part when
     * {@code dailyNewLimit} new sides were introduced today.
     */
    public List<ReviewItem> queue(ReviewScope scope, int dailyNewLimit, int dailyReviewLimit) {
        LocalDate today = LocalDate.now(clock);
        int newDone = logs.countOn(today, clock.getZone(), true);
        int reviewsDone = logs.countOn(today, clock.getZone(), false) - newDone;
        int reviewRoom = Math.max(0, dailyReviewLimit - reviewsDone);
        int newRoom = Math.max(0, dailyNewLimit - newDone);

        Map<GoalId, Goal> goalsInScope = goalsIn(scope);
        Map<AreaId, String> areaNames = areas.findAll().stream().collect(Collectors.toMap(Area::id, Area::name));
        List<Card> candidates = cards.findDueOrNew(today).stream()
                .filter(card -> goalsInScope.containsKey(card.goalId()))
                .sorted(Comparator.comparing(Card::createdAt))
                .toList();

        List<Slot> due = new ArrayList<>();
        List<Slot> fresh = new ArrayList<>();
        for (Card card : candidates) {
            for (CardSide side : CardSide.values()) {
                if (!card.hasSide(side)) {
                    continue;
                }
                CardSchedule schedule = card.schedule(side);
                if (schedule.isNew()) {
                    fresh.add(new Slot(card, side, schedule));
                } else if (schedule.isDueOn(today)) {
                    due.add(new Slot(card, side, schedule));
                }
            }
        }
        due.sort(Comparator.comparing((Slot slot) -> slot.schedule().dueOn()));

        List<ReviewItem> queue = new ArrayList<>();
        due.stream().limit(reviewRoom).forEach(slot -> queue.add(item(slot, goalsInScope, areaNames)));
        fresh.stream().limit(newRoom).forEach(slot -> queue.add(item(slot, goalsInScope, areaNames)));
        return queue;
    }

    /** How many items {@link #queue} would offer now: the number on the Review button. */
    public int dueCount(ReviewScope scope, int dailyNewLimit, int dailyReviewLimit) {
        return queue(scope, dailyNewLimit, dailyReviewLimit).size();
    }

    /** Records the answer to one side of a card, moving its schedule and appending to the history. */
    public GradeResult grade(CardId cardId, CardSide side, Grade grade, int seconds) {
        Card card = load(cardId);
        ReviewLog log = card.review(side, grade, seconds, LocalDate.now(clock), clock.instant());
        cards.save(card);
        logs.append(log);
        CardSchedule next = card.schedule(side);
        return new GradeResult(next.dueOn(), next.intervalDays());
    }

    /**
     * The interval, in days, each grade would give one side of a card right now (0 means due again
     * today): the captions of the four review buttons. Read-only, so peeking never moves a schedule.
     */
    public Map<Grade, Integer> previewIntervals(CardId cardId, CardSide side) {
        CardSchedule current = load(cardId).schedule(side);
        LocalDate today = LocalDate.now(clock);
        Map<Grade, Integer> intervals = new EnumMap<>(Grade.class);
        for (Grade grade : Grade.values()) {
            intervals.put(grade, Sm2Scheduler.next(current, grade, today).intervalDays());
        }
        return intervals;
    }

    /**
     * The earliest day after today on which an already studied side within {@code scope} comes due, for
     * the "come back on …" hint when nothing is left to review. Empty when no studied side is waiting.
     */
    public Optional<LocalDate> nextDue(ReviewScope scope) {
        LocalDate today = LocalDate.now(clock);
        Optional<LocalDate> earliest = Optional.empty();
        for (Goal goal : goalsIn(scope).values()) {
            for (Card card : cards.findByGoal(goal.id())) {
                for (CardSchedule schedule : card.schedules().values()) {
                    if (!schedule.isNew() && schedule.dueOn().isAfter(today)
                            && (earliest.isEmpty() || schedule.dueOn().isBefore(earliest.get()))) {
                        earliest = Optional.of(schedule.dueOn());
                    }
                }
            }
        }
        return earliest;
    }

    // ---------- import ----------

    /**
     * Creates one card per line of {@code text}: front and back separated by a tab, else a
     * {@code ;}, else a {@code ,} (see {@link DelimitedCards}). Blank lines and lines starting with
     * {@code #} are ignored; lines that cannot become a card are reported as {@code "line N: …"}
     * and the others are still imported. The cards are filed under {@code stepId} when it is given.
     */
    public ImportResult importDelimited(GoalId goalId, StepId stepId, String text) {
        Goal goal = goal(goalId);
        ensureStepBelongsTo(goal, stepId);
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();
        List<String> skipped = new ArrayList<>();
        int imported = 0;
        String[] lines = text == null ? new String[0] : text.split("\\R", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            var sides = DelimitedCards.split(line);
            if (sides.isEmpty()) {
                skipped.add("line " + (index + 1) + ": needs a front and a back separated by a tab, ';' or ','");
                continue;
            }
            try {
                // One microsecond apart (what PostgreSQL stores), so the cards keep the order of the text.
                Card card = Card.create(goalId, new CardDetails(stepId, sides.get().front(), sides.get().back(),
                        Set.of(), false), today, now.plus(imported, ChronoUnit.MICROS));
                cards.save(card);
                imported++;
            } catch (DomainValidationException invalid) {
                skipped.add("line " + (index + 1) + ": " + invalid.getMessage());
            }
        }
        return new ImportResult(imported, skipped);
    }

    // ---------- helpers ----------

    private record Slot(Card card, CardSide side, CardSchedule schedule) {
    }

    private ReviewItem item(Slot slot, Map<GoalId, Goal> goalsInScope, Map<AreaId, String> areaNames) {
        Card card = slot.card();
        Goal goal = goalsInScope.get(card.goalId());
        boolean reverse = slot.side() == CardSide.REVERSE;
        return new ReviewItem(card.id(), slot.side(), reverse ? card.back() : card.front(),
                reverse ? card.front() : card.back(), goal.title(), areaNames.get(goal.areaId()),
                slot.schedule().isNew(), goal.id());
    }

    private Map<GoalId, Goal> goalsIn(ReviewScope scope) {
        Map<GoalId, Goal> inScope = new HashMap<>();
        for (Goal goal : goals.findAll()) {
            boolean matches = (scope.goalId() == null || scope.goalId().equals(goal.id()))
                    && (scope.areaId() == null || scope.areaId().equals(goal.areaId()));
            if (matches) {
                inScope.put(goal.id(), goal);
            }
        }
        return inScope;
    }

    private void ensureStepBelongsTo(Goal goal, StepId stepId) {
        if (stepId != null && goal.steps().stream().map(RoadmapStep::id).noneMatch(stepId::equals)) {
            throw new NotFoundException("Step", stepId);
        }
    }

    private Card load(CardId id) {
        return cards.findById(id).orElseThrow(() -> new NotFoundException("Card", id));
    }

    private Goal goal(GoalId id) {
        return goals.findById(id).orElseThrow(() -> new NotFoundException("Goal", id));
    }
}
