package com.edutrack.domain.measure;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.skill.CompetencyId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class MeasureTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final GoalId goalId = GoalId.newId();

    private static MeasureDetails details(String name, String unit, String max, String target) {
        return new MeasureDetails(name, unit, max == null ? null : new BigDecimal(max),
                target == null ? null : new BigDecimal(target), null, null, null);
    }

    private Measure measure() {
        return Measure.create(goalId, details("IELTS band", "band", "9", "7"), 0, NOW);
    }

    @Test
    void createsWithDefaultsForDirectionAndCombineRule() {
        Measure measure = measure();

        assertThat(measure.name()).isEqualTo("IELTS band");
        assertThat(measure.unit()).isEqualTo("band");
        assertThat(measure.maxValue()).isEqualByComparingTo("9");
        assertThat(measure.target()).isEqualByComparingTo("7");
        assertThat(measure.direction()).isEqualTo(Direction.HIGHER_IS_BETTER);
        assertThat(measure.combineRule()).isEqualTo(CombineRule.NONE);
        assertThat(measure.competencyId()).isNull();
        assertThat(measure.goalId()).isEqualTo(goalId);
        assertThat(measure.createdAt()).isEqualTo(NOW);
    }

    @Test
    void keepsExplicitDirectionRuleAndCompetency() {
        CompetencyId competency = CompetencyId.newId();

        Measure measure = Measure.create(goalId, new MeasureDetails("Mile time", "min", null, new BigDecimal("7"),
                Direction.LOWER_IS_BETTER, CombineRule.SUM, competency), 1, NOW);

        assertThat(measure.direction()).isEqualTo(Direction.LOWER_IS_BETTER);
        assertThat(measure.combineRule()).isEqualTo(CombineRule.SUM);
        assertThat(measure.competencyId()).isEqualTo(competency);
        assertThat(measure.position()).isEqualTo(1);
        assertThat(measure.maxValue()).isNull();
    }

    @Test
    void textIsStrippedAndBlankUnitBecomesNull() {
        Measure measure = Measure.create(goalId, details("  Typing speed  ", "  ", null, null), 0, NOW);

        assertThat(measure.name()).isEqualTo("Typing speed");
        assertThat(measure.unit()).isNull();
    }

    @Test
    void nameIsRequiredAndAtMost80Characters() {
        DomainValidationException blank = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details(" ", null, null, null), 0, NOW));
        DomainValidationException tooLong = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("x".repeat(81), null, null, null), 0, NOW));

        assertThat(blank.code()).isEqualTo("required");
        assertThat(blank.args()).containsExactly("Name");
        assertThat(tooLong.code()).isEqualTo("too-long");
        assertThat(tooLong.args()).containsExactly("Name", 80);
    }

    @Test
    void unitIsAtMost20Characters() {
        DomainValidationException error = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("A", "x".repeat(21), null, null), 0, NOW));

        assertThat(error.code()).isEqualTo("too-long");
        assertThat(error.args()).containsExactly("Unit", 20);
    }

    @Test
    void maximumMustBePositive() {
        DomainValidationException zero = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("A", null, "0", null), 0, NOW));
        DomainValidationException negative = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("A", null, "-1", null), 0, NOW));

        assertThat(zero.code()).isEqualTo("not-positive");
        assertThat(zero.args()).containsExactly("Maximum value");
        assertThat(negative.code()).isEqualTo("negative");
    }

    @Test
    void targetCannotBeNegativeButMayBeZero() {
        DomainValidationException negative = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("Errors", null, null, "-1"), 0, NOW));

        assertThat(negative.code()).isEqualTo("negative");
        assertThat(negative.args()).containsExactly("Target");
        assertThat(Measure.create(goalId, details("Errors", null, null, "0"), 0, NOW).target()).isEqualByComparingTo("0");
    }

    @Test
    void targetCannotExceedTheMaximum() {
        DomainValidationException error = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("A", null, "9", "9.5"), 0, NOW));

        assertThat(error.code()).isEqualTo("too-large");
        assertThat(error.args()).containsExactly("Target", new BigDecimal("9.00"));
        assertThat(Measure.create(goalId, details("A", null, "9", "9"), 0, NOW).target()).isEqualByComparingTo("9");
    }

    @Test
    void valuesAreRoundedToTwoDecimalsAndCapped() {
        Measure measure = Measure.create(goalId, details("A", null, "9.256", "7.004"), 0, NOW);
        DomainValidationException huge = catchThrowableOfType(DomainValidationException.class,
                () -> Measure.create(goalId, details("A", null, "1000000", null), 0, NOW));

        assertThat(measure.maxValue()).isEqualTo(new BigDecimal("9.26"));
        assertThat(measure.target()).isEqualTo(new BigDecimal("7.00"));
        assertThat(huge.code()).isEqualTo("too-large");
    }

    @Test
    void aRejectedUpdateChangesNothing() {
        Measure measure = measure();

        assertThatThrownBy(() -> measure.update(details("New name", null, "9", "12")))
                .isInstanceOf(DomainValidationException.class);

        assertThat(measure.name()).isEqualTo("IELTS band");
        assertThat(measure.target()).isEqualByComparingTo("7");
    }

    @Test
    void updateReplacesEveryEditableField() {
        Measure measure = measure();
        CompetencyId competency = CompetencyId.newId();

        measure.update(new MeasureDetails("Mile time", "min", null, null, Direction.LOWER_IS_BETTER, CombineRule.AVERAGE, competency));

        assertThat(measure.name()).isEqualTo("Mile time");
        assertThat(measure.maxValue()).isNull();
        assertThat(measure.target()).isNull();
        assertThat(measure.direction()).isEqualTo(Direction.LOWER_IS_BETTER);
        assertThat(measure.combineRule()).isEqualTo(CombineRule.AVERAGE);
        assertThat(measure.competencyId()).isEqualTo(competency);
    }

    @Test
    void negativePositionIsRejected() {
        assertThatThrownBy(() -> Measure.create(goalId, details("A", null, null, null), -1, NOW))
                .isInstanceOf(DomainValidationException.class);
    }
}
