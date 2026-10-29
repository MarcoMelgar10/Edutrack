package com.edutrack.domain.journal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate root: a dated journal entry, either a free note or the weekly check-in. Entries may be
 * linked to an area, a goal and a step, by id only; the links are cleared by the database when the
 * target is deleted, because a reflection is worth keeping after the goal it was about is gone.
 *
 * <p>A check-in carries a confidence (1 to 5) and a snapshot of the minutes studied that week,
 * taken by the service when it is written.
 */
public final class JournalEntry {

    public static final int TITLE_MAX_LENGTH = 120;
    public static final int BODY_MAX_LENGTH = 20_000;
    public static final int MIN_CONFIDENCE = 1;
    public static final int MAX_CONFIDENCE = 5;

    private final JournalEntryId id;
    private final EntryKind kind;
    private final Instant createdAt;
    private LocalDate writtenOn;
    private String title;
    private String body;
    private AreaId areaId;
    private GoalId goalId;
    private StepId stepId;
    private Integer confidence;
    private Integer weekMinutes;
    private Instant updatedAt;

    private JournalEntry(JournalEntryId id, EntryKind kind, EntryDetails details, Integer weekMinutes,
                         Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.kind = Validation.required(kind, "Kind");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        apply(details, weekMinutes);
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** {@code weekMinutes} is the check-in's snapshot of the week's study time; ignored for notes. */
    public static JournalEntry create(EntryKind kind, EntryDetails details, Integer weekMinutes, LocalDate today,
                                      Instant now) {
        ensureNotInFuture(details, today);
        return new JournalEntry(JournalEntryId.newId(), kind, details, weekMinutes, now, now);
    }

    /** Rebuilds an entry from storage, without the date check (it was valid when written). */
    public static JournalEntry restore(JournalEntryId id, EntryKind kind, LocalDate writtenOn, String title,
                                       String body, AreaId areaId, GoalId goalId, StepId stepId,
                                       Integer confidence, Integer weekMinutes, Instant createdAt,
                                       Instant updatedAt) {
        return new JournalEntry(id, kind,
                new EntryDetails(writtenOn, title, body, areaId, goalId, stepId, confidence),
                weekMinutes, createdAt, updatedAt);
    }

    /** Keeps the week snapshot of a check-in as it is. */
    public void update(EntryDetails details, LocalDate today, Instant now) {
        update(details, weekMinutes, today, now);
    }

    /** All-or-nothing; {@code weekMinutes} replaces the snapshot of a check-in (its date may have moved). */
    public void update(EntryDetails details, Integer weekMinutes, LocalDate today, Instant now) {
        Objects.requireNonNull(now, "now");
        ensureNotInFuture(details, today);
        apply(details, weekMinutes);
        this.updatedAt = now;
    }

    private static void ensureNotInFuture(EntryDetails details, LocalDate today) {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(today, "today");
        if (details.writtenOn() != null && details.writtenOn().isAfter(today)) {
            throw new DomainValidationException("future-date", "Date cannot be in the future.", "Date");
        }
    }

    /** All-or-nothing: every value is validated before any field changes. */
    private void apply(EntryDetails details, Integer weekMinutes) {
        Objects.requireNonNull(details, "details");
        LocalDate validDate = Validation.required(details.writtenOn(), "Date");
        String validTitle = Validation.optionalText(details.title(), "Title", TITLE_MAX_LENGTH);
        String validBody = validBody(details.body());
        Integer validConfidence = null;
        Integer validWeekMinutes = null;
        if (kind == EntryKind.CHECK_IN) {
            validConfidence = Validation.required(details.confidence(), "Confidence");
            if (validConfidence < MIN_CONFIDENCE || validConfidence > MAX_CONFIDENCE) {
                throw new DomainValidationException("out-of-range",
                        "Confidence must be between " + MIN_CONFIDENCE + " and " + MAX_CONFIDENCE + ".",
                        "Confidence", MIN_CONFIDENCE, MAX_CONFIDENCE);
            }
            validWeekMinutes = weekMinutes;
            if (validWeekMinutes != null && validWeekMinutes < 0) {
                throw new DomainValidationException("negative", "Week minutes cannot be negative.", "Week minutes");
            }
        }
        this.writtenOn = validDate;
        this.title = validTitle;
        this.body = validBody;
        this.areaId = details.areaId();
        this.goalId = details.goalId();
        this.stepId = details.stepId();
        this.confidence = validConfidence;
        this.weekMinutes = validWeekMinutes;
    }

    /** The Markdown exactly as written (whitespace is formatting); required for a note. */
    private String validBody(String markdown) {
        String text = markdown == null ? "" : markdown;
        if (kind == EntryKind.NOTE && text.isBlank()) {
            throw new DomainValidationException("required", "Note is required.", "Note");
        }
        if (text.length() > BODY_MAX_LENGTH) {
            throw new DomainValidationException("too-long", "Note must be at most " + BODY_MAX_LENGTH + " characters.",
                    "Note", BODY_MAX_LENGTH);
        }
        return text;
    }

    public JournalEntryId id() {
        return id;
    }

    public EntryKind kind() {
        return kind;
    }

    public LocalDate writtenOn() {
        return writtenOn;
    }

    public String title() {
        return title;
    }

    /** Markdown; empty (never {@code null}) when a check-in has no reflection. */
    public String body() {
        return body;
    }

    public AreaId areaId() {
        return areaId;
    }

    public GoalId goalId() {
        return goalId;
    }

    public StepId stepId() {
        return stepId;
    }

    /** 1 to 5 for a check-in; {@code null} for a note. */
    public Integer confidence() {
        return confidence;
    }

    /** Minutes studied in the check-in's week, as of writing; {@code null} for a note. */
    public Integer weekMinutes() {
        return weekMinutes;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JournalEntry entry && id.equals(entry.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "JournalEntry[" + kind + " " + writtenOn + "]";
    }
}
