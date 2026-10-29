package com.edutrack.domain.measure;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.shared.Validation;
import com.edutrack.domain.skill.CompetencyId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * Aggregate root: a number a goal tracks over time (an exam band, a typing speed, errors per
 * page). Results recorded against the measure form its series; this aggregate only holds how to
 * read them: the unit, the scale, the target, which way is better and how the parts of one
 * attempt combine.
 */
public final class Measure {

    public static final int NAME_MAX_LENGTH = 80;
    public static final int UNIT_MAX_LENGTH = 20;
    public static final BigDecimal MAX_VALUE = new BigDecimal("999999.99");

    private final MeasureId id;
    private final GoalId goalId;
    private final Instant createdAt;
    private String name;
    private String unit;
    private BigDecimal maxValue;
    private BigDecimal target;
    private Direction direction;
    private CombineRule combineRule;
    private CompetencyId competencyId;
    private int position;

    private Measure(MeasureId id, GoalId goalId, MeasureDetails details, int position, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.goalId = Objects.requireNonNull(goalId, "goalId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        update(details);
        moveTo(position);
    }

    public static Measure create(GoalId goalId, MeasureDetails details, int position, Instant now) {
        return new Measure(MeasureId.newId(), goalId, details, position, now);
    }

    public static Measure restore(MeasureId id, GoalId goalId, MeasureDetails details, int position, Instant createdAt) {
        return new Measure(id, goalId, details, position, createdAt);
    }

    /** All-or-nothing: every value is validated before any field changes. */
    public void update(MeasureDetails details) {
        Objects.requireNonNull(details, "details");
        String validName = Validation.requiredText(details.name(), "Name", NAME_MAX_LENGTH);
        String validUnit = Validation.optionalText(details.unit(), "Unit", UNIT_MAX_LENGTH);
        BigDecimal validMax = details.maxValue() == null ? null : amount(details.maxValue(), "Maximum value");
        if (validMax != null && validMax.signum() == 0) {
            throw new DomainValidationException("not-positive", "Maximum value must be greater than 0.", "Maximum value");
        }
        BigDecimal validTarget = details.target() == null ? null : amount(details.target(), "Target");
        if (validMax != null && validTarget != null && validTarget.compareTo(validMax) > 0) {
            throw new DomainValidationException("too-large", "Target must be at most " + validMax + ".", "Target", validMax);
        }
        this.name = validName;
        this.unit = validUnit;
        this.maxValue = validMax;
        this.target = validTarget;
        this.direction = details.direction() == null ? Direction.HIGHER_IS_BETTER : details.direction();
        this.combineRule = details.combineRule() == null ? CombineRule.NONE : details.combineRule();
        this.competencyId = details.competencyId();
    }

    public void moveTo(int position) {
        this.position = Validation.position(position);
    }

    private static BigDecimal amount(BigDecimal value, String field) {
        if (value.signum() < 0) {
            throw new DomainValidationException("negative", field + " cannot be negative.", field);
        }
        BigDecimal twoDecimals = value.setScale(2, RoundingMode.HALF_UP);
        if (twoDecimals.compareTo(MAX_VALUE) > 0) {
            throw new DomainValidationException("too-large", field + " must be at most " + MAX_VALUE + ".", field, MAX_VALUE);
        }
        return twoDecimals;
    }

    public MeasureId id() {
        return id;
    }

    public GoalId goalId() {
        return goalId;
    }

    public String name() {
        return name;
    }

    public String unit() {
        return unit;
    }

    /** The top of the scale, or {@code null} when the measure has none (a time, a word count). */
    public BigDecimal maxValue() {
        return maxValue;
    }

    /** The value to reach, or {@code null} when there is no target. */
    public BigDecimal target() {
        return target;
    }

    public Direction direction() {
        return direction;
    }

    public CombineRule combineRule() {
        return combineRule;
    }

    /** The competency this measure evidences, or {@code null}. */
    public CompetencyId competencyId() {
        return competencyId;
    }

    public int position() {
        return position;
    }

    public Instant createdAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Measure measure && id.equals(measure.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "Measure[" + name + "]";
    }
}
