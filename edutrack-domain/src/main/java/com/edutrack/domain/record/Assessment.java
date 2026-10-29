package com.edutrack.domain.record;

import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/**
 * A formal result: an exam, test, quiz or certificate score, e.g. "IELTS mock test · 6.5 / 9".
 * The maximum is optional (some results are just a score or a band). A result can be recorded
 * against a goal's {@code Measure} (optionally as one {@code part} of an attempt, e.g. "Listening");
 * that the measure exists and its maximum allows the score is checked by the application service,
 * since it spans two aggregates.
 */
public final class Assessment {

    public static final int TITLE_MAX_LENGTH = 160;
    public static final int NOTE_MAX_LENGTH = 500;
    public static final int PART_MAX_LENGTH = 60;
    public static final BigDecimal MAX_SCORE = new BigDecimal("999999.99");

    private final AssessmentId id;
    private String title;
    private BigDecimal score;
    private BigDecimal maxScore;
    private LocalDate takenOn;
    private String note;
    private MeasureId measureId;
    private String part;

    private Assessment(AssessmentId id, AssessmentDetails details) {
        this.id = Objects.requireNonNull(id, "id");
        apply(details);
    }

    static Assessment create(AssessmentDetails details, LocalDate today) {
        ensureNotInFuture(details, today);
        return new Assessment(AssessmentId.newId(), details);
    }

    /** Rebuilds a result from storage, without the date check (it was valid when recorded). */
    public static Assessment restore(AssessmentId id, String title, BigDecimal score, BigDecimal maxScore,
                                     LocalDate takenOn, String note) {
        return restore(id, title, score, maxScore, takenOn, note, null, null);
    }

    public static Assessment restore(AssessmentId id, String title, BigDecimal score, BigDecimal maxScore,
                                     LocalDate takenOn, String note, MeasureId measureId, String part) {
        return new Assessment(id, new AssessmentDetails(title, score, maxScore, takenOn, note, measureId, part));
    }

    /** A result records something already taken, so its date cannot be after {@code today}. */
    void update(AssessmentDetails details, LocalDate today) {
        ensureNotInFuture(details, today);
        apply(details);
    }

    private static void ensureNotInFuture(AssessmentDetails details, LocalDate today) {
        Objects.requireNonNull(details, "details");
        Objects.requireNonNull(today, "today");
        if (details.takenOn() != null && details.takenOn().isAfter(today)) {
            throw new DomainValidationException("future-date", "Date cannot be in the future.", "Date");
        }
    }

    /** All-or-nothing: every value is validated before any field changes. */
    private void apply(AssessmentDetails details) {
        Objects.requireNonNull(details, "details");
        String validTitle = Validation.requiredText(details.title(), "Title", TITLE_MAX_LENGTH);
        BigDecimal validScore = amount(Validation.required(details.score(), "Score"), "Score");
        BigDecimal validMax = details.maxScore() == null ? null : amount(details.maxScore(), "Maximum score");
        if (validMax != null && validMax.signum() == 0) {
            throw new DomainValidationException("not-positive", "Maximum score must be greater than 0.", "Maximum score");
        }
        if (validMax != null && validScore.compareTo(validMax) > 0) {
            throw new DomainValidationException("score-above-max", "Score cannot be higher than the maximum score.");
        }
        LocalDate validDate = Validation.required(details.takenOn(), "Date");
        String validNote = Validation.optionalText(details.note(), "Note", NOTE_MAX_LENGTH);
        String validPart = Validation.optionalText(details.part(), "Part", PART_MAX_LENGTH);
        this.title = validTitle;
        this.score = validScore;
        this.maxScore = validMax;
        this.takenOn = validDate;
        this.note = validNote;
        this.measureId = details.measureId();
        this.part = validPart;
    }

    private static BigDecimal amount(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new DomainValidationException("negative", field + " cannot be negative.", field);
        }
        BigDecimal twoDecimals = value.setScale(2, RoundingMode.HALF_UP);
        if (twoDecimals.compareTo(MAX_SCORE) > 0) {
            throw new DomainValidationException("too-large", field + " must be at most " + MAX_SCORE + ".", field, MAX_SCORE);
        }
        return twoDecimals;
    }

    /** Score as a share of the maximum (6.5 / 9 → 72%), when a maximum is known. */
    public Optional<Progress> percentage() {
        return maxScore == null ? Optional.empty() : Optional.of(Progress.ofRatio(score, maxScore));
    }

    public AssessmentId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public BigDecimal score() {
        return score;
    }

    public BigDecimal maxScore() {
        return maxScore;
    }

    public LocalDate takenOn() {
        return takenOn;
    }

    public String note() {
        return note;
    }

    /** The measure this result counts towards, or {@code null}. */
    public MeasureId measureId() {
        return measureId;
    }

    /** Which part of an attempt this is ("Listening" in a mock exam), or {@code null}. */
    public String part() {
        return part;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Assessment assessment && id.equals(assessment.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
