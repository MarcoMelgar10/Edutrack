package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.Assessment;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.Exercise;
import com.edutrack.domain.record.LearningRecord;
import com.edutrack.domain.record.LearningRecordRepository;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.Resource;
import com.edutrack.domain.record.ResourceDetails;
import com.edutrack.domain.record.ResourceType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaLearningRecordRepositoryAdapter.class})
class JpaLearningRecordRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-03-01T09:30:15.123456Z");
    private static final Instant DONE_AT = Instant.parse("2026-03-05T18:00:00.654321Z");
    private static final Instant LATER = Instant.parse("2026-03-09T07:45:10.000001Z");
    // After every result date used below, so none of them counts as a future result.
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private LearningRecordRepository records;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void roundTripsAStepRecordWithEverythingInIt() {
        Goal goal = saveGoalWithSteps(3);
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        LearningRecord record = fullRecord(owner);

        LearningRecord saved = records.save(record);
        flushAndClear();
        LearningRecord loaded = records.findByOwner(owner).orElseThrow();

        assertSameRecord(saved, record);
        assertSameRecord(loaded, record);
        assertThat(loaded.notes()).isEqualTo("# Grammar\n\n  indented  \n");
        assertThat(loaded.resources()).extracting(Resource::title).containsExactly("BBC Learning English", "Printed grammar");
        assertThat(loaded.resources()).extracting(Resource::url).containsExactly("https://bbc.co.uk/learningenglish", null);
        assertThat(loaded.resources()).extracting(Resource::type).containsExactly(ResourceType.ARTICLE, ResourceType.BOOK);
        assertThat(loaded.resources()).extracting(Resource::reviewed).containsExactly(true, false);
        assertThat(loaded.exercises()).extracting(Exercise::title).containsExactly("Essay", "Speaking", "Listening");
        assertThat(loaded.exercises()).extracting(Exercise::doneAt).containsExactly(null, DONE_AT, null);
        assertThat(loaded.assessments()).extracting(Assessment::maxScore)
                .containsExactly(new BigDecimal("9.00"), null);
        assertThat(loaded.assessments()).extracting(Assessment::score)
                .containsExactly(new BigDecimal("6.50"), new BigDecimal("7.00"));
        assertThat(loaded.updatedAt()).isEqualTo(record.updatedAt());
    }

    @Test
    void noRecordMeansEmpty() {
        Goal goal = saveGoalWithSteps(1);

        assertThat(records.findByOwner(RecordOwner.ofStep(goal.steps().getFirst().id()))).isEmpty();
        assertThat(records.findByOwner(RecordOwner.ofGoal(goal.id()))).isEmpty();
    }

    @Test
    void savesAnEditedRecordExactly() {
        Goal goal = saveGoalWithSteps(1);
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        records.save(fullRecord(owner));
        flushAndClear();

        LearningRecord record = records.findByOwner(owner).orElseThrow();
        Resource firstResource = record.resources().getFirst();
        Exercise essay = record.exercises().get(0);
        Exercise listening = record.exercises().get(2);
        Assessment second = record.assessments().get(1);

        record.removeResource(firstResource.id(), LATER);
        record.moveExercise(listening.id(), MoveDirection.UP, LATER);          // Essay, Listening, Speaking
        record.markExerciseDone(essay.id(), true, LATER);
        record.updateAssessment(second.id(),
                new AssessmentDetails("Reading test", new BigDecimal("8"), new BigDecimal("10"),
                        LocalDate.of(2026, 3, 8), "Better"), TODAY, LATER);
        record.updateNotes("Edited", LATER);
        record.addExercise("New one", LATER);

        LearningRecord saved = records.save(record);
        flushAndClear();
        LearningRecord reloaded = records.findByOwner(owner).orElseThrow();

        assertSameRecord(saved, record);
        assertSameRecord(reloaded, record);
        assertThat(reloaded.resources()).extracting(Resource::title).containsExactly("Printed grammar");
        assertThat(reloaded.exercises()).extracting(Exercise::title)
                .containsExactly("Essay", "Listening", "Speaking", "New one");
        assertThat(reloaded.exercises().getFirst().doneAt()).isEqualTo(LATER);
        assertThat(reloaded.assessments().get(1).title()).isEqualTo("Reading test");
        assertThat(reloaded.assessments().get(1).percentage().orElseThrow().rounded()).isEqualTo(80);
        assertThat(reloaded.updatedAt()).isEqualTo(LATER);
        assertThat(childCount("learning_resource", record)).isEqualTo(1);
        assertThat(childCount("learning_exercise", record)).isEqualTo(4);
        assertThat(childCount("learning_result", record)).isEqualTo(2);
    }

    @Test
    void roundTripsAGoalRecord() {
        Goal goal = saveGoalWithSteps(1);
        RecordOwner owner = RecordOwner.ofGoal(goal.id());
        LearningRecord record = LearningRecord.create(owner, NOW);
        record.addAssessment(new AssessmentDetails("Cambridge B2", new BigDecimal("172"), new BigDecimal("190"),
                LocalDate.of(2026, 12, 1), "Passed"), TODAY, NOW);

        records.save(record);
        flushAndClear();
        LearningRecord loaded = records.findByOwner(owner).orElseThrow();

        assertSameRecord(loaded, record);
        assertThat(loaded.owner()).isEqualTo(owner);
        assertThat(loaded.owner().isStep()).isFalse();
        assertThat(records.findByOwner(RecordOwner.ofStep(goal.steps().getFirst().id()))).isEmpty();
    }

    @Test
    void summariesForStepsInOneQuery() {
        Goal goal = saveGoalWithSteps(3);
        StepId withRecord = goal.steps().get(0).id();
        StepId withoutRecord = goal.steps().get(1).id();
        StepId blankNotes = goal.steps().get(2).id();
        records.save(fullRecord(RecordOwner.ofStep(withRecord)));
        LearningRecord whitespace = LearningRecord.create(RecordOwner.ofStep(blankNotes), NOW);
        whitespace.updateNotes(" \n\t ", NOW);
        records.save(whitespace);
        flushAndClear();

        Map<StepId, RecordSummary> summaries = records.summariesForSteps(List.of(withRecord, withoutRecord, blankNotes));

        assertThat(summaries).containsOnlyKeys(withRecord, blankNotes);
        assertThat(summaries.get(withRecord)).isEqualTo(new RecordSummary(true, 2, 1, 3, 2));
        assertThat(summaries.get(blankNotes)).isEqualTo(RecordSummary.EMPTY);
    }

    @Test
    void summariesForNoStepsIsEmpty() {
        assertThat(records.summariesForSteps(List.of())).isEmpty();
    }

    @Test
    void summaryMatchesTheDomainSummary() {
        Goal goal = saveGoalWithSteps(1);
        StepId stepId = goal.steps().getFirst().id();
        LearningRecord record = fullRecord(RecordOwner.ofStep(stepId));
        records.save(record);
        flushAndClear();

        assertThat(records.summariesForSteps(List.of(stepId)).get(stepId)).isEqualTo(record.summary());
    }

    @Test
    void deletingAGoalDeletesItsRecordsAndItsStepRecords() {
        Goal goal = saveGoalWithSteps(2);
        Goal kept = saveGoalWithSteps(1);
        RecordOwner goalOwner = RecordOwner.ofGoal(goal.id());
        RecordOwner stepOwner = RecordOwner.ofStep(goal.steps().getFirst().id());
        RecordOwner keptOwner = RecordOwner.ofStep(kept.steps().getFirst().id());
        LearningRecord goalRecord = LearningRecord.create(goalOwner, NOW);
        goalRecord.addAssessment(new AssessmentDetails("Exam", BigDecimal.ONE, null, LocalDate.of(2026, 3, 1), null), TODAY, NOW);
        records.save(goalRecord);
        records.save(fullRecord(stepOwner));
        records.save(fullRecord(keptOwner));
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(records.findByOwner(goalOwner)).isEmpty();
        assertThat(records.findByOwner(stepOwner)).isEmpty();
        assertThat(records.findByOwner(keptOwner)).isPresent();
        assertThat(count("select count(*) from learning_record where id = :id", goalRecord.id().value())).isZero();
    }

    @Test
    void aSecondNewRecordForTheSameStepIsRejected() {
        Goal goal = saveGoalWithSteps(1);
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        records.save(LearningRecord.create(owner, NOW));

        assertThatThrownBy(() -> records.save(LearningRecord.create(owner, LATER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ---------- helpers ----------

    private Goal saveGoalWithSteps(int steps) {
        Area area = areas.save(Area.create("Area " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW));
        Goal goal = Goal.create(area.id(), "Reach B2", null, null, 0, NOW);
        for (int i = 1; i <= steps; i++) {
            goal.addStep(new StepDetails("Step " + i, null, null), StepStatus.NOT_STARTED, NOW);
        }
        Goal saved = goals.save(goal);
        entityManager.flush();
        return saved;
    }

    /** Notes, 2 resources (the second without link), 3 exercises (the second done) and 2 results (the second without maximum). */
    private static LearningRecord fullRecord(RecordOwner owner) {
        LearningRecord record = LearningRecord.create(owner, NOW);
        record.updateNotes("# Grammar\n\n  indented  \n", NOW);
        Resource bbc = record.addResource(
                new ResourceDetails("BBC Learning English", "bbc.co.uk/learningenglish", ResourceType.ARTICLE), NOW);
        record.markResourceReviewed(bbc.id(), true, NOW);
        record.addResource(new ResourceDetails("Printed grammar", null, ResourceType.BOOK), NOW);
        record.addExercise("Essay", NOW);
        Exercise speaking = record.addExercise("Speaking", NOW);
        record.addExercise("Listening", NOW);
        record.markExerciseDone(speaking.id(), true, DONE_AT);
        record.addAssessment(new AssessmentDetails("IELTS mock", new BigDecimal("6.5"), new BigDecimal("9"),
                LocalDate.of(2026, 3, 2), "Listening was hard"), TODAY, NOW);
        record.addAssessment(new AssessmentDetails("Reading test", new BigDecimal("7"), null,
                LocalDate.of(2026, 3, 4), null), TODAY, NOW);
        return record;
    }

    private static void assertSameRecord(LearningRecord actual, LearningRecord expected) {
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        assertThat(actual.resources()).extracting(Resource::id)
                .containsExactlyElementsOf(expected.resources().stream().map(Resource::id).toList());
        assertThat(actual.exercises()).extracting(Exercise::id)
                .containsExactlyElementsOf(expected.exercises().stream().map(Exercise::id).toList());
        assertThat(actual.assessments()).extracting(Assessment::id)
                .containsExactlyElementsOf(expected.assessments().stream().map(Assessment::id).toList());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long childCount(String table, LearningRecord record) {
        return count("select count(*) from " + table + " where record_id = :id", record.id().value());
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
