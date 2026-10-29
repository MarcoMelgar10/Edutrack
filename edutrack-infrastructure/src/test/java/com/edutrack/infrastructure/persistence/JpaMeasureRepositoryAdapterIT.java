package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureRepository;
import com.edutrack.domain.record.Assessment;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.record.MeasuredResult;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static com.edutrack.infrastructure.persistence.GoalFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaMeasureRepositoryAdapter.class,
        JpaLearningRecordRepositoryAdapter.class, JpaCompetencyRepositoryAdapter.class})
class JpaMeasureRepositoryAdapterIT {

    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private MeasureRepository measures;

    @Autowired
    private LearningRecordRepository records;

    @Autowired
    private CompetencyRepository competencies;

    @Autowired
    private TestEntityManager entityManager;

    private Goal goalWithSteps(int steps) {
        Goal goal = GoalFixtures.saveGoalWithSteps(areas, goals, steps);
        entityManager.flush();
        return goal;
    }

    private static MeasureDetails band() {
        return new MeasureDetails("IELTS band", "band", new BigDecimal("9"), new BigDecimal("7"),
                Direction.HIGHER_IS_BETTER, CombineRule.AVERAGE, null);
    }

    private static AssessmentDetails result(MeasureId measure, String part, String score, LocalDate day) {
        return new AssessmentDetails("IELTS mock", new BigDecimal(score), null, day, null, measure, part);
    }

    @Test
    void roundTripsAFullMeasure() {
        Goal goal = goalWithSteps(1);
        Competency competency = competencies.save(Competency.create(goal.id(), new CompetencyDetails("Listening", null, 5), 0, NOW));
        Measure measure = Measure.create(goal.id(), new MeasureDetails("IELTS band", "band", new BigDecimal("9"),
                new BigDecimal("7.5"), Direction.HIGHER_IS_BETTER, CombineRule.AVERAGE, competency.id()), 3, NOW);

        Measure saved = measures.save(measure);
        flushAndClear();
        Measure loaded = measures.findById(measure.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(measure);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(measure);
        assertThat(loaded.maxValue()).isEqualTo(new BigDecimal("9.00"));
        assertThat(loaded.target()).isEqualTo(new BigDecimal("7.50"));
        assertThat(loaded.competencyId()).isEqualTo(competency.id());
        assertThat(loaded.position()).isEqualTo(3);
    }

    @Test
    void roundTripsAMeasureWithOnlyTheRequiredFields() {
        Goal goal = goalWithSteps(1);
        Measure measure = Measure.create(goal.id(), new MeasureDetails("Mile time", null, null, null,
                Direction.LOWER_IS_BETTER, null, null), 0, NOW);

        measures.save(measure);
        flushAndClear();
        Measure loaded = measures.findById(measure.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(measure);
        assertThat(loaded.unit()).isNull();
        assertThat(loaded.maxValue()).isNull();
        assertThat(loaded.target()).isNull();
        assertThat(loaded.direction()).isEqualTo(Direction.LOWER_IS_BETTER);
        assertThat(loaded.combineRule()).isEqualTo(CombineRule.NONE);
    }

    @Test
    void savesAnEditedMeasure() {
        Goal goal = goalWithSteps(1);
        Measure measure = measures.save(Measure.create(goal.id(), band(), 0, NOW));
        flushAndClear();

        Measure edited = measures.findById(measure.id()).orElseThrow();
        edited.update(new MeasureDetails("Overall", null, null, null, Direction.LOWER_IS_BETTER, CombineRule.SUM, null));
        edited.moveTo(4);
        measures.save(edited);
        flushAndClear();

        assertThat(measures.findById(measure.id()).orElseThrow()).usingRecursiveComparison().isEqualTo(edited);
        assertThat(measures.findById(measure.id()).orElseThrow().maxValue()).isNull();
    }

    @Test
    void findByGoalIsOrderedByPositionAndLimitedToTheGoal() {
        Goal goal = goalWithSteps(1);
        Goal other = goalWithSteps(1);
        measures.save(Measure.create(goal.id(), new MeasureDetails("B", null, null, null, null, null, null), 1, NOW));
        measures.save(Measure.create(goal.id(), new MeasureDetails("A", null, null, null, null, null, null), 0, NOW));
        measures.save(Measure.create(other.id(), new MeasureDetails("Other", null, null, null, null, null, null), 0, NOW));
        flushAndClear();

        assertThat(measures.findByGoal(goal.id())).extracting(Measure::name).containsExactly("A", "B");
    }

    @Test
    void deletingAMeasureKeepsItsResultsAndUnlinksThem() {
        Goal goal = goalWithSteps(1);
        Measure measure = measures.save(Measure.create(goal.id(), band(), 0, NOW));
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        LearningRecord record = LearningRecord.create(owner, NOW);
        Assessment linked = record.addAssessment(result(measure.id(), "Listening", "6.5", LocalDate.of(2026, 3, 1)), TODAY, NOW);
        record.addAssessment(new AssessmentDetails("Quiz", BigDecimal.TEN, null, LocalDate.of(2026, 3, 2), null), TODAY, NOW);
        records.save(record);
        flushAndClear();

        measures.delete(measure.id());
        measures.delete(measure.id());
        flushAndClear();

        assertThat(measures.findById(measure.id())).isEmpty();
        LearningRecord reloaded = records.findByOwner(owner).orElseThrow();
        assertThat(reloaded.assessments()).hasSize(2);
        Assessment unlinked = reloaded.assessments().stream().filter(a -> a.id().equals(linked.id())).findFirst().orElseThrow();
        assertThat(unlinked.measureId()).isNull();
        assertThat(unlinked.part()).isEqualTo("Listening");
        assertThat(unlinked.score()).isEqualTo(new BigDecimal("6.50"));
        assertThat(records.resultsForMeasure(measure.id())).isEmpty();
    }

    @Test
    void deletingTheGoalDeletesItsMeasures() {
        Goal goal = goalWithSteps(1);
        Measure measure = measures.save(Measure.create(goal.id(), band(), 0, NOW));
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(measures.findById(measure.id())).isEmpty();
    }

    @Test
    void aResultCannotPointAtAMissingMeasure() {
        Goal goal = goalWithSteps(1);
        LearningRecord record = LearningRecord.create(RecordOwner.ofGoal(goal.id()), NOW);
        record.addAssessment(result(MeasureId.newId(), null, "6", LocalDate.of(2026, 3, 1)), TODAY, NOW);

        assertThatThrownBy(() -> records.save(record)).hasStackTraceContaining("foreign key");
    }

    // ---------- results of a record ----------

    @Test
    void resultsRoundTripTheirMeasureAndPart() {
        Goal goal = goalWithSteps(1);
        Measure measure = measures.save(Measure.create(goal.id(), band(), 0, NOW));
        RecordOwner owner = RecordOwner.ofGoal(goal.id());
        LearningRecord record = LearningRecord.create(owner, NOW);
        record.addAssessment(result(measure.id(), "  Speaking ", "7", LocalDate.of(2026, 3, 1)), TODAY, NOW);
        record.addAssessment(new AssessmentDetails("Plain", BigDecimal.ONE, null, LocalDate.of(2026, 3, 2), null), TODAY, NOW);

        records.save(record);
        flushAndClear();
        LearningRecord loaded = records.findByOwner(owner).orElseThrow();

        assertThat(loaded.assessments()).extracting(Assessment::measureId).containsExactly(measure.id(), null);
        assertThat(loaded.assessments()).extracting(Assessment::part).containsExactly("Speaking", null);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(record);
    }

    @Test
    void resultsForMeasureListsResultsOfStepsAndGoalOldestFirst() {
        Goal goal = goalWithSteps(2);
        Measure measure = measures.save(Measure.create(goal.id(), band(), 0, NOW));
        Measure other = measures.save(Measure.create(goal.id(),
                new MeasureDetails("Typing", "wpm", null, null, null, null, null), 1, NOW));
        RecordOwner stepOwner = RecordOwner.ofStep(goal.steps().get(0).id());
        RecordOwner goalOwner = RecordOwner.ofGoal(goal.id());

        LearningRecord stepRecord = LearningRecord.create(stepOwner, NOW);
        stepRecord.addAssessment(result(other.id(), null, "50", LocalDate.of(2026, 3, 1)), TODAY, NOW);          // position 0, other measure
        Assessment later = stepRecord.addAssessment(result(measure.id(), "Writing", "6.5", LocalDate.of(2026, 6, 1)), TODAY, NOW);
        stepRecord.addAssessment(new AssessmentDetails("Unlinked", BigDecimal.ONE, null, LocalDate.of(2026, 1, 1), null), TODAY, NOW);
        LearningRecord goalRecord = LearningRecord.create(goalOwner, NOW);
        Assessment earlier = goalRecord.addAssessment(result(measure.id(), "Listening", "7", LocalDate.of(2026, 3, 1)), TODAY, NOW);
        records.save(stepRecord);
        records.save(goalRecord);
        flushAndClear();

        assertThat(records.resultsForMeasure(measure.id())).containsExactly(
                new MeasuredResult(goalOwner, earlier.id(), "IELTS mock", "Listening", new BigDecimal("7.00"),
                        LocalDate.of(2026, 3, 1), 0),
                new MeasuredResult(stepOwner, later.id(), "IELTS mock", "Writing", new BigDecimal("6.50"),
                        LocalDate.of(2026, 6, 1), 1));
        assertThat(records.resultsForMeasure(other.id())).hasSize(1);
        assertThat(records.resultsForMeasure(MeasureId.newId())).isEmpty();
    }

    @Test
    void theDatabaseRejectsATargetAboveTheMaximum() {
        Goal goal = goalWithSteps(1);

        assertThatThrownBy(() -> entityManager.getEntityManager().createNativeQuery(
                "insert into measure (id, goal_id, name, max_value, target, position, created_at) "
                        + "values (:id, :goal, 'X', 9, 10, 0, now())")
                .setParameter("id", UUID.randomUUID()).setParameter("goal", goal.id().value()).executeUpdate())
                .hasStackTraceContaining("violates check constraint");
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
