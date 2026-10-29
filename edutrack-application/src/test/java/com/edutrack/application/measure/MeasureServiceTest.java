package com.edutrack.application.measure;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.support.InMemoryCompetencyRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryLearningRecordRepository;
import com.edutrack.application.support.InMemoryMeasureRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureSeries.Point;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MeasureServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryMeasureRepository measures = new InMemoryMeasureRepository();
    private final InMemoryLearningRecordRepository records = new InMemoryLearningRecordRepository();
    private final InMemoryCompetencyRepository competencies = new InMemoryCompetencyRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final MeasureService service = new MeasureService(measures, records, competencies, goals, clock);
    private final LearningRecordService recordService = new LearningRecordService(records, goals, measures, clock);
    private final Goal goal = goals.save(Goal.create(AreaId.newId(), "IELTS 7", null, null, 0, NOW));
    private final RecordOwner owner = RecordOwner.ofGoal(goal.id());

    private static MeasureDetails ielts() {
        return new MeasureDetails("IELTS band", "band", new BigDecimal("9"), new BigDecimal("7"),
                Direction.HIGHER_IS_BETTER, CombineRule.AVERAGE, null);
    }

    private void result(MeasureId measure, String part, String score, LocalDate day) {
        recordService.addResult(owner, new AssessmentDetails("IELTS mock", new BigDecimal(score), null, day, null,
                measure, part));
    }

    @Test
    void createdMeasuresAreAppendedAndStartEmpty() {
        MeasureView band = service.create(goal.id(), ielts());
        MeasureView speed = service.create(goal.id(), new MeasureDetails("Typing", "wpm", null, null, null, null, null));

        assertThat(band.position()).isZero();
        assertThat(speed.position()).isEqualTo(1);
        assertThat(band.points()).isEmpty();
        assertThat(band.latest()).isNull();
        assertThat(band.best()).isNull();
        assertThat(band.meetsTarget()).isFalse();
        assertThat(band.resultCount()).isZero();
        assertThat(speed.direction()).isEqualTo(Direction.HIGHER_IS_BETTER);
        assertThat(speed.combineRule()).isEqualTo(CombineRule.NONE);
        assertThat(service.forGoal(goal.id())).extracting(MeasureView::name).containsExactly("IELTS band", "Typing");
    }

    @Test
    void creatingForAMissingGoalFails() {
        assertThatThrownBy(() -> service.create(com.edutrack.domain.goal.GoalId.newId(), ielts()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void invalidDetailsAreRejected() {
        assertThatThrownBy(() -> service.create(goal.id(), new MeasureDetails(" ", null, null, null, null, null, null)))
                .isInstanceOf(DomainValidationException.class);
        assertThat(measures.findByGoal(goal.id())).isEmpty();
    }

    @Test
    void theSeriesCombinesThePartsOfEachAttempt() {
        MeasureId id = service.create(goal.id(), ielts()).id();
        LocalDate march = LocalDate.of(2026, 3, 1);
        LocalDate june = LocalDate.of(2026, 6, 1);
        result(id, "Listening", "6.5", march);
        result(id, "Reading", "7.0", march);
        result(id, "Writing", "6.0", march);
        result(id, "Speaking", "6.5", march);
        result(id, "Listening", "7.5", june);
        result(id, "Reading", "7.5", june);

        MeasureView view = service.get(id);

        assertThat(view.points()).containsExactly(
                new Point(march, new BigDecimal("6.50"), 4),
                new Point(june, new BigDecimal("7.50"), 2));
        assertThat(view.latest()).isEqualTo(new Point(june, new BigDecimal("7.50"), 2));
        assertThat(view.best()).isEqualTo(view.latest());
        assertThat(view.meetsTarget()).isTrue();
        assertThat(view.resultCount()).isEqualTo(6);
    }

    @Test
    void meetsTargetLooksAtTheLatestAttemptOnly() {
        MeasureId id = service.create(goal.id(), ielts()).id();
        result(id, null, "8", LocalDate.of(2026, 3, 1));
        result(id, null, "6", LocalDate.of(2026, 6, 1));

        MeasureView view = service.get(id);

        assertThat(view.meetsTarget()).isFalse();
        assertThat(view.best().value()).isEqualByComparingTo("8");
    }

    @Test
    void lowerIsBetterMeasuresPickTheLowestAsBestAndCompareDownwards() {
        MeasureId id = service.create(goal.id(), new MeasureDetails("Mile", "min", null, new BigDecimal("8"),
                Direction.LOWER_IS_BETTER, CombineRule.NONE, null)).id();
        result(id, null, "9.5", LocalDate.of(2026, 3, 1));
        result(id, null, "7.8", LocalDate.of(2026, 4, 1));
        result(id, null, "8.2", LocalDate.of(2026, 5, 1));

        MeasureView view = service.get(id);

        assertThat(view.best().value()).isEqualByComparingTo("7.8");
        assertThat(view.latest().value()).isEqualByComparingTo("8.2");
        assertThat(view.meetsTarget()).isFalse();
    }

    @Test
    void noTargetMeansTargetNeverMet() {
        MeasureId id = service.create(goal.id(), new MeasureDetails("Words", null, null, null, null, null, null)).id();
        result(id, null, "100", LocalDate.of(2026, 3, 1));

        assertThat(service.get(id).meetsTarget()).isFalse();
    }

    @Test
    void measuresOnlyCountTheirOwnResults() {
        MeasureId band = service.create(goal.id(), ielts()).id();
        MeasureId other = service.create(goal.id(), new MeasureDetails("Typing", "wpm", null, null, null, null, null)).id();
        result(band, null, "6", LocalDate.of(2026, 3, 1));
        result(other, null, "50", LocalDate.of(2026, 3, 1));
        recordService.addResult(owner, new AssessmentDetails("Unrelated", BigDecimal.ONE, null, LocalDate.of(2026, 3, 1), null));

        assertThat(service.get(band).resultCount()).isEqualTo(1);
        assertThat(service.get(other).resultCount()).isEqualTo(1);
    }

    @Test
    void updateChangesTheDefinitionAndKeepsTheResults() {
        MeasureId id = service.create(goal.id(), ielts()).id();
        result(id, "Listening", "6", LocalDate.of(2026, 3, 1));

        MeasureView updated = service.update(id, new MeasureDetails("IELTS overall", "band", new BigDecimal("9"),
                new BigDecimal("6"), Direction.HIGHER_IS_BETTER, CombineRule.SUM, null));

        assertThat(updated.name()).isEqualTo("IELTS overall");
        assertThat(updated.combineRule()).isEqualTo(CombineRule.SUM);
        assertThat(updated.resultCount()).isEqualTo(1);
        assertThat(updated.meetsTarget()).isTrue();
    }

    @Test
    void unknownMeasuresAreReported() {
        assertThatThrownBy(() -> service.get(MeasureId.newId())).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(MeasureId.newId(), ielts())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aMeasureCanPointAtACompetencyOfTheSameGoal() {
        Competency listening = competencies.save(Competency.create(goal.id(), new CompetencyDetails("Listening", null, 5), 0, NOW));

        MeasureView view = service.create(goal.id(), new MeasureDetails("Listening band", "band", null, null, null, null,
                listening.id()));

        assertThat(view.competencyId()).isEqualTo(listening.id());
    }

    @Test
    void aCompetencyOfAnotherGoalOrAMissingOneIsRejected() {
        Goal other = goals.save(Goal.create(AreaId.newId(), "Other", null, null, 0, NOW));
        Competency foreign = competencies.save(Competency.create(other.id(), new CompetencyDetails("Foreign", null, 5), 0, NOW));
        MeasureId id = service.create(goal.id(), ielts()).id();

        assertThatThrownBy(() -> service.create(goal.id(), new MeasureDetails("A", null, null, null, null, null, foreign.id())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(id, new MeasureDetails("A", null, null, null, null, null, foreign.id())))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(goal.id(), new MeasureDetails("A", null, null, null, null, null,
                com.edutrack.domain.skill.CompetencyId.newId())))
                .isInstanceOf(NotFoundException.class);
        assertThat(measures.findByGoal(goal.id())).hasSize(1);
    }

    @Test
    void deleteRemovesTheMeasure() {
        MeasureId id = service.create(goal.id(), ielts()).id();

        service.delete(id);

        assertThat(service.forGoal(goal.id())).isEmpty();
        service.delete(id);
    }
}
