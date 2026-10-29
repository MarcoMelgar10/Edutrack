package com.edutrack.domain.record;

import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/** The optional link of a result to a measure, and the part of an attempt it records. */
class AssessmentMeasureLinkTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final LearningRecord record = LearningRecord.create(RecordOwner.ofStep(StepId.newId()), NOW);
    private final MeasureId measure = MeasureId.newId();

    private AssessmentDetails details(MeasureId measureId, String part) {
        return new AssessmentDetails("IELTS mock", new BigDecimal("6.5"), null, TODAY, null, measureId, part);
    }

    @Test
    void resultsWithoutAMeasureHaveNoMeasureAndNoPart() {
        Assessment legacy = record.addAssessment(
                new AssessmentDetails("Quiz", BigDecimal.TEN, null, TODAY, null), TODAY, NOW);

        assertThat(legacy.measureId()).isNull();
        assertThat(legacy.part()).isNull();
    }

    @Test
    void aResultKeepsItsMeasureAndStrippedPart() {
        Assessment result = record.addAssessment(details(measure, "  Listening "), TODAY, NOW);

        assertThat(result.measureId()).isEqualTo(measure);
        assertThat(result.part()).isEqualTo("Listening");
    }

    @Test
    void aBlankPartBecomesNull() {
        Assessment result = record.addAssessment(details(measure, "   "), TODAY, NOW);

        assertThat(result.part()).isNull();
    }

    @Test
    void aPartIsAtMost60Characters() {
        DomainValidationException error = catchThrowableOfType(DomainValidationException.class,
                () -> record.addAssessment(details(measure, "x".repeat(61)), TODAY, NOW));

        assertThat(error.code()).isEqualTo("too-long");
        assertThat(error.args()).containsExactly("Part", 60);
        assertThat(record.assessments()).isEmpty();
    }

    @Test
    void updatingCanMoveAResultToAnotherMeasureOrUnlinkIt() {
        Assessment result = record.addAssessment(details(measure, "Reading"), TODAY, NOW);
        MeasureId other = MeasureId.newId();

        record.updateAssessment(result.id(), details(other, null), TODAY, NOW);
        assertThat(result.measureId()).isEqualTo(other);
        assertThat(result.part()).isNull();

        record.updateAssessment(result.id(), details(null, null), TODAY, NOW);
        assertThat(result.measureId()).isNull();
    }

    @Test
    void restoreAcceptsTheMeasureAndPartAndTheOldSignatureStillWorks() {
        AssessmentId id = AssessmentId.newId();

        Assessment linked = Assessment.restore(id, "Mock", new BigDecimal("7"), null, TODAY, null, measure, "Writing");
        Assessment old = Assessment.restore(id, "Mock", new BigDecimal("7"), null, TODAY, null);

        assertThat(linked.measureId()).isEqualTo(measure);
        assertThat(linked.part()).isEqualTo("Writing");
        assertThat(old.measureId()).isNull();
        assertThat(old.part()).isNull();
    }
}
