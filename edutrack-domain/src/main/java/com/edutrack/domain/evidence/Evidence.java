package com.edutrack.domain.evidence;

import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;
import com.edutrack.domain.shared.WebLinks;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Aggregate root: proof of learning for a goal, optionally tied to one of its steps and to a
 * competency — a project, a recording, an essay, a certificate. It needs at least a link, a file
 * or a note, so an entry is never just a title.
 */
public final class Evidence {

    public static final int TITLE_MAX_LENGTH = 160;
    public static final int NOTE_MAX_LENGTH = 2000;

    private final EvidenceId id;
    private final GoalId goalId;
    private StepId stepId;
    private EvidenceKind kind;
    private String title;
    private LocalDate takenOn;
    private String url;
    private AttachmentId attachmentId;
    private CompetencyId competencyId;
    private String note;
    private final Instant createdAt;

    private Evidence(EvidenceId id, GoalId goalId, EvidenceDetails details, AttachmentId attachmentId,
                     Instant createdAt, boolean requireContent) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.attachmentId = attachmentId;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        apply(details, requireContent);
    }

    /** {@code today} is the learner's current date: evidence cannot be dated after it. */
    public static Evidence create(GoalId goalId, EvidenceDetails details, AttachmentId attachmentId,
                                  LocalDate today, Instant now) {
        ensureNotInFuture(details, today);
        return new Evidence(EvidenceId.newId(), goalId, details, attachmentId, now, true);
    }

    /**
     * Rebuilds evidence from storage, without the date and content checks: it was valid when recorded
     * and may have lost its file since (the step that owned the file was deleted).
     */
    public static Evidence restore(EvidenceId id, GoalId goalId, StepId stepId, EvidenceKind kind, String title,
                                   LocalDate takenOn, String url, AttachmentId attachmentId, CompetencyId competencyId,
                                   String note, Instant createdAt) {
        return new Evidence(id, goalId,
                new EvidenceDetails(stepId, kind, title, takenOn, url, note, competencyId), attachmentId, createdAt, false);
    }

    /** All-or-nothing: every value is validated before any field changes. */
    public void update(EvidenceDetails details, LocalDate today) {
        ensureNotInFuture(details, today);
        apply(details, true);
    }

    /** Points at a stored file, replacing the previous one (the caller deletes that file). */
    public void attach(AttachmentId newAttachmentId) {
        this.attachmentId = Objects.requireNonNull(newAttachmentId, "attachmentId");
    }

    /** Drops the file reference; refused when the evidence would be left with nothing to show. */
    public void detach() {
        ensureNotEmpty(url, null, note);
        this.attachmentId = null;
    }

    private static void ensureNotInFuture(EvidenceDetails details, LocalDate today) {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(today, "today");
        if (details.takenOn() != null && details.takenOn().isAfter(today)) {
            throw new DomainValidationException("future-date", "Date cannot be in the future.", "Date");
        }
    }

    private void apply(EvidenceDetails details, boolean requireContent) {
        Objects.requireNonNull(details, "details");
        EvidenceKind validKind = Validation.required(details.kind(), "Type");
        String validTitle = Validation.requiredText(details.title(), "Title", TITLE_MAX_LENGTH);
        LocalDate validDate = Validation.required(details.takenOn(), "Date");
        String validUrl = WebLinks.normalize(details.url(), "Link");
        String validNote = Validation.optionalText(details.note(), "Note", NOTE_MAX_LENGTH);
        if (requireContent) {
            ensureNotEmpty(validUrl, attachmentId, validNote);
        }
        this.stepId = details.stepId();
        this.kind = validKind;
        this.title = validTitle;
        this.takenOn = validDate;
        this.url = validUrl;
        this.competencyId = details.competencyId();
        this.note = validNote;
    }

    private static void ensureNotEmpty(String url, AttachmentId attachment, String note) {
        if (url == null && attachment == null && note == null) {
            throw new DomainValidationException("evidence-empty", "Add a link, a file or a note.");
        }
    }

    public EvidenceId id() {
        return id;
    }

    public GoalId goalId() {
        return goalId;
    }

    /** The step this proves, or {@code null} when it belongs to the goal as a whole. */
    public StepId stepId() {
        return stepId;
    }

    public EvidenceKind kind() {
        return kind;
    }

    public String title() {
        return title;
    }

    public LocalDate takenOn() {
        return takenOn;
    }

    /** Normalized http(s) link, or {@code null}. */
    public String url() {
        return url;
    }

    public AttachmentId attachmentId() {
        return attachmentId;
    }

    /** The competency this demonstrates, or {@code null}. */
    public CompetencyId competencyId() {
        return competencyId;
    }

    public String note() {
        return note;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Evidence evidence && id.equals(evidence.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
