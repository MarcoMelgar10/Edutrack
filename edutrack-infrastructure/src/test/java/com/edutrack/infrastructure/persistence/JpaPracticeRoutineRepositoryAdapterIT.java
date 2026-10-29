package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.practice.PracticeRoutineRepository;
import com.edutrack.domain.practice.RoutineDetails;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaPracticeRoutineRepositoryAdapter.class})
class JpaPracticeRoutineRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00.123456Z");

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private PracticeRoutineRepository routines;

    @Autowired
    private TestEntityManager entityManager;

    private Goal goal;
    private StepId step;

    private void givenAGoalWithAStep() {
        Area area = areas.save(Area.create("English " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW));
        goal = Goal.create(area.id(), "Speak fluently", null, null, 0, NOW);
        step = goal.addStep(new StepDetails("Speaking", null, null), StepStatus.NOT_STARTED, NOW).id();
        goals.save(goal);
        entityManager.flush();
    }

    private Goal anotherGoal() {
        Area area = areas.save(Area.create("Music " + UUID.randomUUID(), null, AreaIcon.BOOK, 1, NOW));
        return goals.save(Goal.create(area.id(), "Guitar", null, null, 0, NOW));
    }

    private PracticeRoutine routine(GoalId goalId, String title, int position) {
        return PracticeRoutine.create(goalId, new RoutineDetails(null, title, Cadence.DAILY, null, 15), position, NOW);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesAndLoadsAWeeklyRoutineWithAllItsFields() {
        givenAGoalWithAStep();
        PracticeRoutine routine = PracticeRoutine.create(goal.id(),
                new RoutineDetails(step, "Shadowing", Cadence.WEEKLY, 5, 20), 2, NOW);

        PracticeRoutine saved = routines.save(routine);
        flushAndClear();
        PracticeRoutine loaded = routines.findById(routine.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(routine);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(routine);
        assertThat(loaded.stepId()).isEqualTo(step);
        assertThat(loaded.cadence()).isEqualTo(Cadence.WEEKLY);
        assertThat(loaded.timesPerWeek()).isEqualTo(5);
        assertThat(loaded.active()).isTrue();
    }

    @Test
    void savesADailyRoutineWithoutAStepAsSevenTimesAWeek() {
        givenAGoalWithAStep();
        PracticeRoutine routine = routine(goal.id(), "Vocabulary", 0);

        routines.save(routine);
        flushAndClear();
        PracticeRoutine loaded = routines.findById(routine.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(routine);
        assertThat(loaded.stepId()).isNull();
        assertThat(loaded.timesPerWeek()).isEqualTo(7);
    }

    @Test
    void findByIdIsEmptyForAnUnknownRoutine() {
        assertThat(routines.findById(PracticeRoutineId.newId())).isEmpty();
    }

    @Test
    void savingAnEditedRoutineUpdatesTheSameRow() {
        givenAGoalWithAStep();
        PracticeRoutine routine = routine(goal.id(), "Vocabulary", 0);
        routines.save(routine);
        flushAndClear();

        PracticeRoutine loaded = routines.findById(routine.id()).orElseThrow();
        loaded.update(new RoutineDetails(step, "Speaking club", Cadence.WEEKLY, 2, 45));
        loaded.pause();
        loaded.moveTo(3);
        routines.save(loaded);
        flushAndClear();

        PracticeRoutine reloaded = routines.findById(routine.id()).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(loaded);
        assertThat(reloaded.active()).isFalse();
        assertThat(reloaded.position()).isEqualTo(3);
        assertThat(count("select count(*) from practice_routine where id = :id", routine.id().value())).isEqualTo(1);
    }

    @Test
    void savingTheSameNewRoutineTwiceKeepsOneRow() {
        givenAGoalWithAStep();
        PracticeRoutine routine = routine(goal.id(), "Vocabulary", 0);

        routines.save(routine);
        routines.save(routine);
        flushAndClear();

        assertThat(count("select count(*) from practice_routine where id = :id", routine.id().value())).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheRoutineAndDeletingAMissingOneDoesNothing() {
        givenAGoalWithAStep();
        PracticeRoutine routine = routine(goal.id(), "Vocabulary", 0);
        PracticeRoutine kept = routine(goal.id(), "Shadowing", 1);
        routines.save(routine);
        routines.save(kept);
        flushAndClear();

        routines.delete(routine.id());
        routines.delete(PracticeRoutineId.newId());
        flushAndClear();

        assertThat(routines.findById(routine.id())).isEmpty();
        assertThat(routines.findById(kept.id())).isPresent();
    }

    @Test
    void findByGoalReturnsThatGoalsRoutinesByPositionPausedOnesIncluded() {
        givenAGoalWithAStep();
        Goal other = anotherGoal();
        PracticeRoutine second = routine(goal.id(), "Second", 1);
        PracticeRoutine first = routine(goal.id(), "First", 0);
        PracticeRoutine paused = routine(goal.id(), "Paused", 2);
        paused.pause();
        PracticeRoutine elsewhere = routine(other.id(), "Elsewhere", 0);
        routines.save(second);
        routines.save(first);
        routines.save(paused);
        routines.save(elsewhere);
        flushAndClear();

        assertThat(routines.findByGoal(goal.id())).extracting(PracticeRoutine::id)
                .containsExactly(first.id(), second.id(), paused.id());
        assertThat(routines.findByGoal(other.id())).extracting(PracticeRoutine::id).containsExactly(elsewhere.id());
        assertThat(routines.findByGoal(GoalId.newId())).isEmpty();
    }

    @Test
    void findActiveReturnsOnlyActiveRoutinesOfEveryGoal() {
        givenAGoalWithAStep();
        Goal other = anotherGoal();
        PracticeRoutine active = routine(goal.id(), "Active", 0);
        PracticeRoutine paused = routine(goal.id(), "Paused", 1);
        paused.pause();
        PracticeRoutine elsewhere = routine(other.id(), "Elsewhere", 1);
        routines.save(active);
        routines.save(paused);
        routines.save(elsewhere);
        flushAndClear();

        assertThat(routines.findActive()).extracting(PracticeRoutine::id)
                .contains(active.id(), elsewhere.id())
                .doesNotContain(paused.id());
    }

    @Test
    void deletingTheGoalDeletesItsRoutinesOnly() {
        givenAGoalWithAStep();
        Goal other = anotherGoal();
        PracticeRoutine mine = routine(goal.id(), "Mine", 0);
        PracticeRoutine theirs = routine(other.id(), "Theirs", 0);
        routines.save(mine);
        routines.save(theirs);
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(routines.findById(mine.id())).isEmpty();
        assertThat(routines.findById(theirs.id())).isPresent();
    }

    @Test
    void deletingTheStepKeepsTheRoutineAndClearsItsStep() {
        givenAGoalWithAStep();
        PracticeRoutine routine = PracticeRoutine.create(goal.id(),
                new RoutineDetails(step, "Shadowing", Cadence.DAILY, null, 20), 0, NOW);
        routines.save(routine);
        flushAndClear();

        Goal loaded = goals.findById(goal.id()).orElseThrow();
        loaded.removeStep(step);
        goals.save(loaded);
        flushAndClear();

        PracticeRoutine reloaded = routines.findById(routine.id()).orElseThrow();
        assertThat(reloaded.stepId()).isNull();
        assertThat(reloaded.title()).isEqualTo("Shadowing");
    }

    // A failed statement aborts the PostgreSQL transaction, so each rejection gets its own test.

    @Test
    void theDatabaseRejectsADailyRoutineThatIsNotSevenTimesAWeek() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert("DAILY", 3, 20)).hasMessageContaining("practice_routine");
    }

    @Test
    void theDatabaseRejectsMoreThanSevenTimesAWeek() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert("WEEKLY", 8, 20)).hasMessageContaining("times_per_week");
    }

    @Test
    void theDatabaseRejectsTargetMinutesAboveSixHundred() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert("WEEKLY", 3, 601)).hasMessageContaining("target_minutes");
    }

    @Test
    void theDatabaseRejectsAnUnknownCadence() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert("MONTHLY", 3, 20)).hasMessageContaining("cadence");
    }

    private void insert(String cadence, int timesPerWeek, int targetMinutes) {
        entityManager.getEntityManager()
                .createNativeQuery("insert into practice_routine "
                        + "(id, goal_id, title, cadence, times_per_week, target_minutes, position, created_at) "
                        + "values (:id, :goal, 'X', :cadence, :times, :minutes, 0, now())")
                .setParameter("id", UUID.randomUUID())
                .setParameter("goal", goal.id().value())
                .setParameter("cadence", cadence)
                .setParameter("times", timesPerWeek)
                .setParameter("minutes", targetMinutes)
                .executeUpdate();
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
