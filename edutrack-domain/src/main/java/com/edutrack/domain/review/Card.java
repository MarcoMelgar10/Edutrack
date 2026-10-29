package com.edutrack.domain.review;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Aggregate root: a flashcard of a goal, optionally filed under one of its roadmap steps.
 *
 * <p>Each studied side has its own SM-2 schedule: {@link CardSide#FORWARD} always, and
 * {@link CardSide#REVERSE} only while the card is reversible, so a vocabulary card can be asked in
 * both directions and each direction is remembered separately. Turning reversible on gives the
 * reverse side a fresh schedule; turning it off drops it.
 */
public final class Card {

    public static final int TEXT_MAX_LENGTH = 4000;
    public static final int TAG_MAX_LENGTH = 40;
    public static final int MAX_TAGS = 20;

    private final CardId id;
    private final GoalId goalId;
    private final Instant createdAt;
    private final Map<CardSide, CardSchedule> schedules = new EnumMap<>(CardSide.class);
    private StepId stepId;
    private String front;
    private String back;
    private Set<String> tags;
    private boolean reversible;

    private Card(CardId id, GoalId goalId, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /** A new card is due from {@code today} on every side it has. */
    public static Card create(GoalId goalId, CardDetails details, LocalDate today, Instant now) {
        Card card = new Card(CardId.newId(), goalId, now);
        card.update(details, today);
        return card;
    }

    /**
     * Rebuilds a card from storage. {@code schedules} must hold FORWARD, and REVERSE exactly when
     * {@code details} is reversible.
     */
    public static Card restore(CardId id, GoalId goalId, CardDetails details, Instant createdAt,
                               Map<CardSide, CardSchedule> schedules) {
        Card card = new Card(id, goalId, createdAt);
        Objects.requireNonNull(schedules, "schedules");
        if (!schedules.containsKey(CardSide.FORWARD)
                || details.reversible() != schedules.containsKey(CardSide.REVERSE)) {
            throw new IllegalArgumentException("Schedules " + schedules.keySet() + " do not match card " + id);
        }
        card.schedules.putAll(schedules);
        card.update(details, null);
        return card;
    }

    /**
     * All-or-nothing: every value is validated before any field changes. A side that appears
     * because the card became reversible starts as new on {@code today}.
     */
    public void update(CardDetails details, LocalDate today) {
        Objects.requireNonNull(details, "details");
        String validFront = Validation.requiredText(details.front(), "Front", TEXT_MAX_LENGTH);
        String validBack = Validation.requiredText(details.back(), "Back", TEXT_MAX_LENGTH);
        Set<String> validTags = validTags(details.tags());
        boolean addsReverse = details.reversible() && !schedules.containsKey(CardSide.REVERSE);
        if (addsReverse || schedules.isEmpty()) {
            Objects.requireNonNull(today, "today");
        }

        this.stepId = details.stepId();
        this.front = validFront;
        this.back = validBack;
        this.tags = validTags;
        this.reversible = details.reversible();
        if (schedules.isEmpty()) {
            schedules.put(CardSide.FORWARD, CardSchedule.newCard(today));
        }
        if (addsReverse) {
            schedules.put(CardSide.REVERSE, CardSchedule.newCard(today));
        } else if (!details.reversible()) {
            schedules.remove(CardSide.REVERSE);
        }
    }

    /**
     * Answers one side: schedules it again with SM-2 and returns the log entry to append to the
     * review history. {@code seconds} is how long the answer took to give.
     */
    public ReviewLog review(CardSide side, Grade grade, int seconds, LocalDate today, Instant now) {
        Objects.requireNonNull(grade, "grade");
        if (seconds < 0) {
            throw new DomainValidationException("negative", "Seconds cannot be negative.", "Seconds");
        }
        CardSchedule current = schedule(side);
        schedules.put(side, Sm2Scheduler.next(current, grade, today));
        return new ReviewLog(ReviewLogId.newId(), id, side, now, grade, seconds, current.isNew());
    }

    /** The schedule of a side; asking for REVERSE on a card that is not reversible is a mistake. */
    public CardSchedule schedule(CardSide side) {
        CardSchedule schedule = schedules.get(Objects.requireNonNull(side, "side"));
        if (schedule == null) {
            throw new IllegalArgumentException("Card " + id + " has no " + side + " side");
        }
        return schedule;
    }

    public boolean hasSide(CardSide side) {
        return schedules.containsKey(side);
    }

    /** Lower-cased, trimmed, without blanks or duplicates, in alphabetical order. */
    private static Set<String> validTags(Set<String> raw) {
        Set<String> tags = new TreeSet<>();
        if (raw != null) {
            for (String tag : raw) {
                String valid = Validation.optionalText(tag, "Tag", TAG_MAX_LENGTH);
                if (valid != null) {
                    tags.add(valid.toLowerCase(Locale.ROOT));
                }
            }
        }
        if (tags.size() > MAX_TAGS) {
            throw new DomainValidationException("too-large", "A card can have at most " + MAX_TAGS + " tags.",
                    "Tags", MAX_TAGS);
        }
        return Collections.unmodifiableSet(tags);
    }

    // ---------- accessors ----------

    public CardId id() {
        return id;
    }

    public GoalId goalId() {
        return goalId;
    }

    /** The roadmap step the card is filed under, or {@code null}. */
    public StepId stepId() {
        return stepId;
    }

    /** Markdown shown first on the forward side. */
    public String front() {
        return front;
    }

    /** Markdown shown as the answer on the forward side. */
    public String back() {
        return back;
    }

    public Set<String> tags() {
        return tags;
    }

    public boolean reversible() {
        return reversible;
    }

    public Instant createdAt() {
        return createdAt;
    }

    /** The schedule of every side the card has; read-only. */
    public Map<CardSide, CardSchedule> schedules() {
        return Collections.unmodifiableMap(schedules);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Card card && id.equals(card.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Card[" + front + "]";
    }
}
