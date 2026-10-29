package com.edutrack.domain.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoadmapStepTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final Goal goal = Goal.create(AreaId.newId(), "Goal", null, null, 0, NOW);

    private RoadmapStep add(String title, String description, String hours) {
        BigDecimal estimate = hours == null ? null : new BigDecimal(hours);
        return goal.addStep(new StepDetails(title, description, estimate), StepStatus.NOT_STARTED, NOW);
    }

    private RoadmapStep withTitle(String title) {
        return add(title, null, null);
    }

    private RoadmapStep withDescription(String description) {
        return add("Step", description, null);
    }

    private RoadmapStep withHours(String hours) {
        return add("Step", null, hours);
    }

    @Test
    void titleIsRequired() {
        assertThatThrownBy(() -> withTitle(null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Title is required.");
    }

    @Test
    void blankTitleIsRejected() {
        assertThatThrownBy(() -> withTitle("   "))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Title is required.");
    }

    @Test
    void titleIsStripped() {
        assertThat(withTitle("  Present perfect  ").title()).isEqualTo("Present perfect");
    }

    @Test
    void titleOf120CharactersIsAccepted() {
        assertThat(withTitle("a".repeat(120)).title()).hasSize(120);
    }

    @Test
    void titleOf121CharactersIsRejected() {
        assertThatThrownBy(() -> withTitle("a".repeat(121)))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Title must be at most 120 characters.");
    }

    @Test
    void blankDescriptionBecomesNull() {
        assertThat(withDescription("   ").description()).isNull();
    }

    @Test
    void descriptionIsStripped() {
        assertThat(withDescription("  Chapter 3  ").description()).isEqualTo("Chapter 3");
    }

    @Test
    void negativeHoursAreRejected() {
        assertThatThrownBy(() -> withHours("-0.5"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Estimated hours cannot be negative.");
    }

    @Test
    void hoursAreRoundedToOneDecimal() {
        assertThat(withHours("2.25").estimatedHours()).isEqualTo(new BigDecimal("2.3"));
    }

    @Test
    void maximumHoursAreAccepted() {
        assertThat(withHours("9999.9").estimatedHours()).isEqualByComparingTo("9999.9");
    }

    @Test
    void hoursAboveTheMaximumAreRejected() {
        assertThatThrownBy(() -> withHours("10000"))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Estimated hours must be at most 9999.9.");
    }

    @Test
    void hoursAreOptional() {
        assertThat(withHours(null).estimatedHours()).isNull();
    }

    @Test
    void aStepIsNotPlannedByDefault() {
        assertThat(withTitle("Listening").plannedFor()).isNull();
    }

    @Test
    void plannedDateCanBeSetChangedAndClearedEvenInThePast() {
        RoadmapStep step = withTitle("Listening");
        LocalDate past = LocalDate.of(2020, 1, 1);

        goal.updateStep(step.id(), new StepDetails("Listening", null, null, past));
        assertThat(step.plannedFor()).isEqualTo(past);

        goal.updateStep(step.id(), new StepDetails("Listening", null, null, LocalDate.of(2027, 5, 1)));
        assertThat(step.plannedFor()).isEqualTo(LocalDate.of(2027, 5, 1));

        goal.updateStep(step.id(), new StepDetails("Listening", null, null));
        assertThat(step.plannedFor()).isNull();
    }

    @Test
    void restoreKeepsThePlannedDate() {
        LocalDate planned = LocalDate.of(2026, 11, 3);

        RoadmapStep restored = RoadmapStep.restore(StepId.newId(), "Step", null, null, planned,
                StepStatus.NOT_STARTED, null);

        assertThat(restored.plannedFor()).isEqualTo(planned);
    }

    @Test
    void updatingAStepAppliesTheSameRules() {
        RoadmapStep step = withTitle("Listening");
        StepDetails invalid = new StepDetails("Listening", null, new BigDecimal("-1"));

        assertThatThrownBy(() -> goal.updateStep(step.id(), invalid))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("cannot be negative");
    }
}
