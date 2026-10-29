package com.edutrack.domain.goal;

import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One step of a goal's roadmap. Steps only change through their {@link Goal}, which keeps
 * their order; that is why the mutators are package-private. What was learned along the way
 * (notes, resources, exercises, results) lives in the step's {@code LearningRecord}.
 */
public final class RoadmapStep {

    public static final int TITLE_MAX_LENGTH = 120;
    public static final BigDecimal MAX_HOURS = new BigDecimal("9999.9");

    private final StepId id;
    private String title;
    private String description;
    private BigDecimal estimatedHours;
    private LocalDate plannedFor;
    private StepStatus status = StepStatus.NOT_STARTED;
    private Instant completedAt;

    private RoadmapStep(StepId id, StepDetails details) {
        this.id = Objects.requireNonNull(id, "id");
        update(details);
    }

    static RoadmapStep create(StepDetails details, StepStatus status, Instant now) {
        RoadmapStep step = new RoadmapStep(StepId.newId(), details);
        step.changeStatus(status, now);
        return step;
    }

    /** Rebuilds a step from storage, keeping the stored completion time as-is. */
    public static RoadmapStep restore(StepId id, String title, String description, BigDecimal estimatedHours,
                                      StepStatus status, Instant completedAt) {
        return restore(id, title, description, estimatedHours, null, status, completedAt);
    }

    public static RoadmapStep restore(StepId id, String title, String description, BigDecimal estimatedHours,
                                      LocalDate plannedFor, StepStatus status, Instant completedAt) {
        RoadmapStep step = new RoadmapStep(id, new StepDetails(title, description, estimatedHours, plannedFor));
        step.status = Validation.required(status, "Status");
        step.completedAt = status == StepStatus.DONE ? completedAt : null;
        return step;
    }

    /** All-or-nothing: every value is validated before any field changes. */
    void update(StepDetails details) {
        Objects.requireNonNull(details, "details");
        String validTitle = Validation.requiredText(details.title(), "Title", TITLE_MAX_LENGTH);
        String validDescription = Validation.optionalText(details.description(), "Description", Validation.DESCRIPTION_MAX_LENGTH);
        BigDecimal validHours = validHours(details.estimatedHours());
        this.title = validTitle;
        this.description = validDescription;
        this.estimatedHours = validHours;
        this.plannedFor = details.plannedFor();
    }

    /** Records when a step becomes Done; leaving Done clears the date. */
    void changeStatus(StepStatus next, Instant now) {
        Validation.required(next, "Status");
        if (next == status) {
            return;
        }
        status = next;
        completedAt = next == StepStatus.DONE ? Objects.requireNonNull(now, "now") : null;
    }

    static BigDecimal validHours(BigDecimal hours) {
        if (hours == null) {
            return null;
        }
        if (hours.signum() < 0) {
            throw new DomainValidationException("negative", "Estimated hours cannot be negative.", "Estimated hours");
        }
        BigDecimal oneDecimal = hours.setScale(1, RoundingMode.HALF_UP);
        if (oneDecimal.compareTo(MAX_HOURS) > 0) {
            throw new DomainValidationException("too-large", "Estimated hours must be at most " + MAX_HOURS + ".", "Estimated hours", MAX_HOURS);
        }
        return oneDecimal;
    }

    public StepId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Hours with one decimal, or {@code null} when not estimated. */
    public BigDecimal estimatedHours() {
        return estimatedHours;
    }

    /** The day the learner plans to work on this step, or {@code null}; it may be in the past. */
    public LocalDate plannedFor() {
        return plannedFor;
    }

    public StepStatus status() {
        return status;
    }

    /** When the step was last marked Done; {@code null} unless it is Done. */
    public Instant completedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RoadmapStep step && id.equals(step.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "RoadmapStep[" + title + ", " + status + "]";
    }
}
