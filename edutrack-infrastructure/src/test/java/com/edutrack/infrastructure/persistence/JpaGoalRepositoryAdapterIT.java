package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class})
class JpaGoalRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-03-01T09:30:15.123456Z");
    private static final Instant DONE_AT = Instant.parse("2026-03-05T18:00:00.654321Z");
    private static final Instant LATER = Instant.parse("2026-03-09T07:45:10.000001Z");

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void savesAndLoadsAGoalWithItsRoadmap() {
        Area area = saveArea("English");
        Goal goal = englishGoal(area, 0);

        Goal saved = goals.save(goal);
        flushAndClear();
        Goal loaded = goals.findById(goal.id()).orElseThrow();

        assertSameGoal(saved, goal);
        assertSameGoal(loaded, goal);
        assertThat(loaded.steps()).extracting(RoadmapStep::title).containsExactly("Grammar", "Listening", "Mock exam");
        assertThat(loaded.steps()).extracting(RoadmapStep::estimatedHours)
                .containsExactly(new BigDecimal("10.0"), new BigDecimal("20.0"), null);
        assertThat(loaded.steps()).extracting(RoadmapStep::status)
                .containsExactly(StepStatus.DONE, StepStatus.IN_PROGRESS, StepStatus.NOT_STARTED);
        assertThat(loaded.steps()).extracting(RoadmapStep::completedAt).containsExactly(DONE_AT, null, null);
        assertThat(loaded.steps().get(0).description()).isEqualTo("Units 1 to 5");
        assertThat(loaded.progress()).isEqualTo(goal.progress());
        assertThat(loaded.progress().percent()).isEqualByComparingTo("32.26");
        assertThat(stepPositions(goal.id())).containsExactly(0, 1, 2);
    }

    @Test
    void savesAGoalWithoutDescriptionDeadlineAndSteps() {
        Area area = saveArea("Consulting");
        Goal goal = Goal.create(area.id(), "Land a client", null, null, 4, NOW);

        goals.save(goal);
        flushAndClear();
        Goal loaded = goals.findById(goal.id()).orElseThrow();

        assertSameGoal(loaded, goal);
        assertThat(loaded.description()).isNull();
        assertThat(loaded.deadline()).isNull();
        assertThat(loaded.steps()).isEmpty();
        assertThat(loaded.progress().percent()).isEqualByComparingTo("0");
    }

    @Test
    void savesTheWeeklyTargetAndPlannedDays() {
        Area area = saveArea("Music");
        Goal goal = Goal.create(area.id(), "Guitar", null, null, 150, 0, NOW);
        goal.addStep(new StepDetails("Chords", null, new BigDecimal("5"), LocalDate.of(2026, 3, 10)),
                StepStatus.NOT_STARTED, NOW);
        goal.addStep(new StepDetails("Scales", null, null, LocalDate.of(2020, 1, 1)), StepStatus.NOT_STARTED, NOW);
        goal.addStep(new StepDetails("Songs", null, null), StepStatus.NOT_STARTED, NOW);

        goals.save(goal);
        flushAndClear();
        Goal loaded = goals.findById(goal.id()).orElseThrow();

        assertSameGoal(loaded, goal);
        assertThat(loaded.weeklyTargetMinutes()).isEqualTo(150);
        assertThat(loaded.steps()).extracting(RoadmapStep::plannedFor)
                .containsExactly(LocalDate.of(2026, 3, 10), LocalDate.of(2020, 1, 1), null);
    }

    @Test
    void changesAndRemovesTheWeeklyTargetAndPlannedDays() {
        Area area = saveArea("Music");
        Goal original = Goal.create(area.id(), "Guitar", null, null, 150, 0, NOW);
        RoadmapStep chords = original.addStep(new StepDetails("Chords", null, null, LocalDate.of(2026, 3, 10)),
                StepStatus.NOT_STARTED, NOW);
        goals.save(original);
        flushAndClear();

        Goal goal = goals.findById(original.id()).orElseThrow();
        goal.changeWeeklyTarget(200);
        goals.save(goal);
        flushAndClear();
        assertThat(goals.findById(goal.id()).orElseThrow().weeklyTargetMinutes()).isEqualTo(200);

        goal = goals.findById(original.id()).orElseThrow();
        goal.changeWeeklyTarget(null);
        goal.updateStep(chords.id(), new StepDetails("Chords", null, null, null));
        goals.save(goal);
        flushAndClear();

        Goal reloaded = goals.findById(goal.id()).orElseThrow();
        assertThat(reloaded.weeklyTargetMinutes()).isNull();
        assertThat(reloaded.steps().getFirst().plannedFor()).isNull();
    }

    @Test
    void theDatabaseRejectsAWeeklyTargetOutsideTheRange() {
        Area area = saveArea("Music");
        entityManager.flush();

        assertThatThrownBy(() -> entityManager.getEntityManager()
                .createNativeQuery("insert into goal (id, area_id, title, position, created_at, weekly_target_minutes) "
                        + "values (:id, :area, 'Bad', 0, now(), 10081)")
                .setParameter("id", UUID.randomUUID())
                .setParameter("area", area.id().value())
                .executeUpdate())
                .hasMessageContaining("weekly_target_minutes");
    }

    @Test
    void findByIdIsEmptyForUnknownGoal() {
        assertThat(goals.findById(GoalId.newId())).isEmpty();
    }

    @Test
    void savingAnUnchangedGoalAgainChangesNothing() {
        Area area = saveArea("English");
        Goal goal = englishGoal(area, 0);
        goals.save(goal);
        flushAndClear();

        Goal reloaded = goals.findById(goal.id()).orElseThrow();
        goals.save(reloaded);
        goals.save(reloaded);
        flushAndClear();

        assertSameGoal(goals.findById(goal.id()).orElseThrow(), goal);
        assertThat(stepCount(goal.id())).isEqualTo(3);
        assertThat(stepPositions(goal.id())).containsExactly(0, 1, 2);
    }

    @Test
    void savingTheSameNewGoalTwiceKeepsOneRowAndItsSteps() {
        Area area = saveArea("English");
        Goal goal = englishGoal(area, 0);

        goals.save(goal);
        goals.save(goal);
        flushAndClear();

        assertThat(goalCount(goal.id())).isEqualTo(1);
        assertThat(stepCount(goal.id())).isEqualTo(3);
        assertSameGoal(goals.findById(goal.id()).orElseThrow(), goal);
    }

    @Test
    void savesAnEditedRoadmapExactly() {
        Area area = saveArea("English");
        Goal original = englishGoal(area, 0);
        goals.save(original);
        flushAndClear();

        Goal goal = goals.findById(original.id()).orElseThrow();
        List<RoadmapStep> before = List.copyOf(goal.steps());
        RoadmapStep grammar = before.get(0);
        RoadmapStep listening = before.get(1);
        RoadmapStep mockExam = before.get(2);

        goal.moveStep(mockExam.id(), MoveDirection.UP);                          // Grammar, Mock exam, Listening
        goal.removeStep(grammar.id());                                           // Mock exam, Listening
        goal.addStep(new StepDetails("Speaking club", "Weekly", new BigDecimal("4.5")),
                StepStatus.NOT_STARTED, LATER);                                  // Mock exam, Listening, Speaking club
        goal.changeStepStatus(listening.id(), StepStatus.DONE, LATER);
        goal.updateStep(mockExam.id(), new StepDetails("Mock exam B2", null, new BigDecimal("3")));
        goal.update("Reach B2 fast", null, LocalDate.of(2027, 1, 31));
        goal.moveTo(2);

        Goal saved = goals.save(goal);
        flushAndClear();
        Goal reloaded = goals.findById(goal.id()).orElseThrow();

        assertThat(reloaded.steps()).extracting(RoadmapStep::title)
                .containsExactly("Mock exam B2", "Listening", "Speaking club");
        assertThat(reloaded.steps()).hasSize(3);
        assertThat(reloaded.steps().get(1).status()).isEqualTo(StepStatus.DONE);
        assertThat(reloaded.steps().get(1).completedAt()).isEqualTo(LATER);
        assertThat(reloaded.steps().get(2).estimatedHours()).isEqualTo(new BigDecimal("4.5"));
        assertThat(reloaded.steps().get(0).estimatedHours()).isEqualTo(new BigDecimal("3.0"));
        assertSameGoal(saved, goal);
        assertSameGoal(reloaded, goal);
        assertThat(reloaded.progress()).isEqualTo(goal.progress());

        // The removed step is really gone, the survivors kept their rows, positions are contiguous.
        assertThat(reloaded.steps()).extracting(RoadmapStep::id).doesNotContain(grammar.id());
        assertThat(reloaded.steps().get(0).id()).isEqualTo(mockExam.id());
        assertThat(reloaded.steps().get(1).id()).isEqualTo(listening.id());
        assertThat(stepCount(goal.id())).isEqualTo(3);
        assertThat(stepPositions(goal.id())).containsExactly(0, 1, 2);
        assertThat(stepExists(grammar.id())).isFalse();
    }

    @Test
    void savesEditsMadeInTheSameSessionWithoutClearing() {
        Area area = saveArea("English");
        Goal original = englishGoal(area, 0);
        goals.save(original);
        // No clear(): the managed entities are still in the persistence context and must be reused.

        Goal goal = goals.findById(original.id()).orElseThrow();
        goal.moveStep(goal.steps().get(2).id(), MoveDirection.UP);
        goal.moveStep(goal.steps().get(0).id(), MoveDirection.DOWN);
        goal.removeStep(goal.steps().get(1).id());
        goal.addStep(new StepDetails("Reading", null, null), StepStatus.IN_PROGRESS, LATER);
        goals.save(goal);
        flushAndClear();

        assertSameGoal(goals.findById(goal.id()).orElseThrow(), goal);
        assertThat(stepCount(goal.id())).isEqualTo(3);
    }

    @Test
    void removingEveryStepEmptiesTheRoadmap() {
        Area area = saveArea("English");
        Goal goal = englishGoal(area, 0);
        goals.save(goal);
        flushAndClear();

        Goal reloaded = goals.findById(goal.id()).orElseThrow();
        reloaded.steps().stream().map(RoadmapStep::id).toList().forEach(reloaded::removeStep);
        goals.save(reloaded);
        flushAndClear();

        assertThat(goals.findById(goal.id()).orElseThrow().steps()).isEmpty();
        assertThat(stepCount(goal.id())).isZero();
        assertThat(goalCount(goal.id())).isEqualTo(1);
    }

    @Test
    void findByAreaReturnsOnlyThatAreasGoalsOrderedWithTheirSteps() {
        Area english = saveArea("English");
        Area consulting = saveArea("Consulting");

        Goal second = englishGoal(english, 1);
        Goal firstLater = Goal.create(english.id(), "First B", null, null, 0, NOW.plusSeconds(60));
        Goal firstEarlier = Goal.create(english.id(), "First A", null, null, 0, NOW);
        firstEarlier.addStep(new StepDetails("Only step", null, new BigDecimal("2.5")), StepStatus.DONE, DONE_AT);
        Goal elsewhere = Goal.create(consulting.id(), "Other area", null, null, 0, NOW);
        elsewhere.addStep(new StepDetails("Elsewhere", null, null), StepStatus.NOT_STARTED, NOW);
        goals.save(second);
        goals.save(firstLater);
        goals.save(firstEarlier);
        goals.save(elsewhere);
        flushAndClear();

        List<Goal> found = goals.findByArea(english.id());

        assertThat(found).extracting(Goal::id).containsExactly(firstEarlier.id(), firstLater.id(), second.id());
        assertSameGoal(found.get(0), firstEarlier);
        assertSameGoal(found.get(1), firstLater);
        assertSameGoal(found.get(2), second);
        assertThat(found.get(0).steps()).hasSize(1);
        assertThat(found.get(2).steps()).extracting(RoadmapStep::title).containsExactly("Grammar", "Listening", "Mock exam");
        assertThat(goals.findByArea(consulting.id())).extracting(Goal::id).containsExactly(elsewhere.id());
    }

    @Test
    void findByAreaIsEmptyWhenTheAreaHasNoGoals() {
        Area area = saveArea("English");
        flushAndClear();

        assertThat(goals.findByArea(area.id())).isEmpty();
    }

    @Test
    void deleteRemovesTheGoalAndItsSteps() {
        Area area = saveArea("English");
        Goal goal = englishGoal(area, 0);
        Goal kept = englishGoal(area, 1);
        goals.save(goal);
        goals.save(kept);
        flushAndClear();
        assertThat(stepCount(goal.id())).isEqualTo(3);

        goals.delete(goal.id());
        flushAndClear();

        assertThat(goals.findById(goal.id())).isEmpty();
        assertThat(goalCount(goal.id())).isZero();
        assertThat(stepCount(goal.id())).isZero();
        assertThat(goals.findById(kept.id())).isPresent();
        assertThat(stepCount(kept.id())).isEqualTo(3);
        assertThat(areas.findById(area.id())).isPresent();
    }

    @Test
    void deletingAMissingGoalDoesNothing() {
        goals.delete(GoalId.newId());
        flushAndClear();
    }

    /**
     * Without an outer transaction every adapter call has its own session, so this proves the
     * adapters map goals (and read their lazy steps) inside their own transaction.
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void loadsStepsWithoutAnOuterTransaction() {
        Area area = Area.create("IT area " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW);
        areas.save(area);
        try {
            Goal goal = englishGoal(area, 0);
            goals.save(goal);

            assertSameGoal(goals.findById(goal.id()).orElseThrow(), goal);
            List<Goal> found = goals.findByArea(area.id());
            assertThat(found).hasSize(1);
            assertSameGoal(found.get(0), goal);

            goal.removeStep(goal.steps().get(0).id());
            goal.addStep(new StepDetails("Writing", null, null), StepStatus.NOT_STARTED, LATER);
            goals.save(goal);
            assertSameGoal(goals.findById(goal.id()).orElseThrow(), goal);
        } finally {
            areas.delete(area.id()); // the database cascade removes the goal and its steps
        }
        assertThat(areas.findById(area.id())).isEmpty();
    }

    // ---------- helpers ----------

    private Area saveArea(String name) {
        Area area = areas.save(Area.create(name, null, AreaIcon.BOOK, 0, NOW));
        entityManager.flush();
        return area;
    }

    /** "Reach B2" with three steps: Done 10 h, In progress 20 h, Not started without estimate. */
    private static Goal englishGoal(Area area, int position) {
        Goal goal = Goal.create(area.id(), "Reach B2", "Pass the Cambridge exam", LocalDate.of(2026, 12, 31),
                position, NOW);
        goal.addStep(new StepDetails("Grammar", "Units 1 to 5", new BigDecimal("10.0")),
                StepStatus.DONE, DONE_AT);
        goal.addStep(new StepDetails("Listening", null, new BigDecimal("20.0")), StepStatus.IN_PROGRESS, NOW);
        goal.addStep(new StepDetails("Mock exam", null, null), StepStatus.NOT_STARTED, NOW);
        return goal;
    }

    private static void assertSameGoal(Goal actual, Goal expected) {
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
        assertThat(actual.steps()).extracting(RoadmapStep::id).containsExactlyElementsOf(
                expected.steps().stream().map(RoadmapStep::id).toList());
        assertThat(actual.progress()).isEqualTo(expected.progress());
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    private long goalCount(GoalId id) {
        return count("select count(*) from goal where id = :id", id.value());
    }

    private long stepCount(GoalId goalId) {
        return count("select count(*) from roadmap_step where goal_id = :id", goalId.value());
    }

    private boolean stepExists(StepId id) {
        return count("select count(*) from roadmap_step where id = :id", id.value()) > 0;
    }

    private List<Integer> stepPositions(GoalId goalId) {
        List<?> rows = entityManager.getEntityManager()
                .createNativeQuery("select position from roadmap_step where goal_id = :id order by position")
                .setParameter("id", goalId.value())
                .getResultList();
        return rows.stream().map(row -> ((Number) row).intValue()).toList();
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
