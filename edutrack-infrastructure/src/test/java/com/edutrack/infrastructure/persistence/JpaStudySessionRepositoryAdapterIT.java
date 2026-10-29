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
import com.edutrack.domain.practice.PracticeRoutineRepository;
import com.edutrack.domain.practice.RoutineDetails;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;
import com.edutrack.domain.session.StudySessionRepository;
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
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaPracticeRoutineRepositoryAdapter.class,
        JpaStudySessionRepositoryAdapter.class})
class JpaStudySessionRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00.123456Z");
    private static final Instant DAY_1 = Instant.parse("2026-10-26T08:00:00.000001Z");
    private static final Instant DAY_2 = Instant.parse("2026-10-27T08:00:00.000002Z");
    private static final Instant DAY_3 = Instant.parse("2026-10-28T08:00:00.000003Z");

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private PracticeRoutineRepository routines;

    @Autowired
    private StudySessionRepository sessions;

    @Autowired
    private TestEntityManager entityManager;

    private Goal goal;
    private StepId step;

    private void givenAGoalWithAStep() {
        Area area = areas.save(Area.create("English " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW));
        goal = Goal.create(area.id(), "Reach B2", null, null, 0, NOW);
        step = goal.addStep(new StepDetails("Grammar", null, null), StepStatus.NOT_STARTED, NOW).id();
        goal.addStep(new StepDetails("Listening", null, null), StepStatus.NOT_STARTED, NOW);
        goals.save(goal);
        entityManager.flush();
    }

    private Goal anotherGoal() {
        Area area = areas.save(Area.create("Music " + UUID.randomUUID(), null, AreaIcon.BOOK, 1, NOW));
        return goals.save(Goal.create(area.id(), "Guitar", null, null, 0, NOW));
    }

    private StudySession session(GoalId goalId, StepId stepId, Instant startedAt, int minutes) {
        return StudySession.create(new SessionDetails(goalId, stepId, null, startedAt, minutes, null),
                SessionSource.MANUAL, NOW);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesAndLoadsASessionWithAllItsFields() {
        givenAGoalWithAStep();
        PracticeRoutine routine = routines.save(PracticeRoutine.create(goal.id(),
                new RoutineDetails(step, "Shadowing", Cadence.DAILY, null, 20), 0, NOW));
        StudySession session = StudySession.create(new SessionDetails(goal.id(), step, routine.id(), DAY_1, 45, "Unit 3"),
                SessionSource.TIMER, NOW);

        StudySession saved = sessions.save(session);
        flushAndClear();
        StudySession loaded = sessions.findById(session.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(session);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(session);
        assertThat(loaded.startedAt()).isEqualTo(DAY_1);
        assertThat(loaded.source()).isEqualTo(SessionSource.TIMER);
    }

    @Test
    void savesASessionWithoutOptionalFields() {
        givenAGoalWithAStep();
        StudySession session = session(goal.id(), null, DAY_1, 30);

        sessions.save(session);
        flushAndClear();
        StudySession loaded = sessions.findById(session.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(session);
        assertThat(loaded.stepId()).isNull();
        assertThat(loaded.routineId()).isNull();
        assertThat(loaded.note()).isNull();
    }

    @Test
    void findByIdIsEmptyForAnUnknownSession() {
        assertThat(sessions.findById(StudySessionId.newId())).isEmpty();
    }

    @Test
    void savingAnEditedSessionUpdatesTheSameRow() {
        givenAGoalWithAStep();
        StudySession session = session(goal.id(), null, DAY_1, 30);
        sessions.save(session);
        flushAndClear();

        StudySession loaded = sessions.findById(session.id()).orElseThrow();
        loaded.update(new SessionDetails(goal.id(), step, null, DAY_2, 50, "Corrected"), NOW);
        sessions.save(loaded);
        flushAndClear();

        assertThat(count("select count(*) from study_session where id = :id", session.id().value())).isEqualTo(1);
        StudySession reloaded = sessions.findById(session.id()).orElseThrow();
        assertThat(reloaded.minutes()).isEqualTo(50);
        assertThat(reloaded.stepId()).isEqualTo(step);
        assertThat(reloaded.startedAt()).isEqualTo(DAY_2);
        assertThat(reloaded.note()).isEqualTo("Corrected");
    }

    @Test
    void savingTheSameNewSessionTwiceKeepsOneRow() {
        givenAGoalWithAStep();
        StudySession session = session(goal.id(), null, DAY_1, 30);

        sessions.save(session);
        sessions.save(session);
        flushAndClear();

        assertThat(count("select count(*) from study_session where id = :id", session.id().value())).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheSessionAndDeletingAMissingOneDoesNothing() {
        givenAGoalWithAStep();
        StudySession session = session(goal.id(), null, DAY_1, 30);
        StudySession kept = session(goal.id(), null, DAY_2, 20);
        sessions.save(session);
        sessions.save(kept);
        flushAndClear();

        sessions.delete(session.id());
        sessions.delete(StudySessionId.newId());
        flushAndClear();

        assertThat(sessions.findById(session.id())).isEmpty();
        assertThat(sessions.findById(kept.id())).isPresent();
    }

    @Test
    void findByGoalReturnsOnlyThatGoalsSessionsNewestFirst() {
        givenAGoalWithAStep();
        Goal other = anotherGoal();
        StudySession oldest = session(goal.id(), null, DAY_1, 10);
        StudySession newest = session(goal.id(), step, DAY_3, 30);
        StudySession middle = session(goal.id(), null, DAY_2, 20);
        StudySession elsewhere = session(other.id(), null, DAY_2, 99);
        sessions.save(oldest);
        sessions.save(newest);
        sessions.save(middle);
        sessions.save(elsewhere);
        flushAndClear();

        assertThat(sessions.findByGoal(goal.id())).extracting(StudySession::id)
                .containsExactly(newest.id(), middle.id(), oldest.id());
        assertThat(sessions.findByGoal(other.id())).extracting(StudySession::id).containsExactly(elsewhere.id());
        assertThat(sessions.findByGoal(GoalId.newId())).isEmpty();
    }

    @Test
    void findByStepReturnsOnlyThatStepsSessionsNewestFirst() {
        givenAGoalWithAStep();
        StepId otherStep = goal.steps().get(1).id();
        StudySession older = session(goal.id(), step, DAY_1, 10);
        StudySession newer = session(goal.id(), step, DAY_3, 30);
        StudySession onOtherStep = session(goal.id(), otherStep, DAY_2, 20);
        StudySession onGoalOnly = session(goal.id(), null, DAY_2, 40);
        sessions.save(older);
        sessions.save(newer);
        sessions.save(onOtherStep);
        sessions.save(onGoalOnly);
        flushAndClear();

        assertThat(sessions.findByStep(step)).extracting(StudySession::id).containsExactly(newer.id(), older.id());
        assertThat(sessions.findByStep(StepId.newId())).isEmpty();
    }

    @Test
    void findStartedBetweenIncludesTheStartExcludesTheEndAndSpansAllGoalsOldestFirst() {
        givenAGoalWithAStep();
        Goal other = anotherGoal();
        StudySession before = session(goal.id(), null, DAY_1.minusSeconds(1), 5);
        StudySession atStart = session(goal.id(), null, DAY_1, 10);
        StudySession inside = session(other.id(), null, DAY_2, 20);
        StudySession late = session(goal.id(), null, DAY_3.minusSeconds(1), 30);
        StudySession atEnd = session(goal.id(), null, DAY_3, 40);
        sessions.save(atEnd);
        sessions.save(late);
        sessions.save(before);
        sessions.save(inside);
        sessions.save(atStart);
        flushAndClear();

        assertThat(sessions.findStartedBetween(DAY_1, DAY_3)).extracting(StudySession::id)
                .containsExactly(atStart.id(), inside.id(), late.id());
        assertThat(sessions.findStartedBetween(DAY_3.plusSeconds(1), DAY_3.plusSeconds(100))).isEmpty();
    }

    @Test
    void deletingTheGoalDeletesItsSessionsOnly() {
        givenAGoalWithAStep();
        Goal other = anotherGoal();
        StudySession mine = session(goal.id(), step, DAY_1, 10);
        StudySession theirs = session(other.id(), null, DAY_1, 10);
        sessions.save(mine);
        sessions.save(theirs);
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(sessions.findById(mine.id())).isEmpty();
        assertThat(sessions.findById(theirs.id())).isPresent();
    }

    @Test
    void deletingTheStepKeepsTheSessionAndClearsItsStep() {
        givenAGoalWithAStep();
        StudySession session = session(goal.id(), step, DAY_1, 10);
        sessions.save(session);
        flushAndClear();

        Goal loaded = goals.findById(goal.id()).orElseThrow();
        loaded.removeStep(step);
        goals.save(loaded);
        flushAndClear();

        StudySession reloaded = sessions.findById(session.id()).orElseThrow();
        assertThat(reloaded.stepId()).isNull();
        assertThat(reloaded.goalId()).isEqualTo(goal.id());
        assertThat(reloaded.minutes()).isEqualTo(10);
    }

    @Test
    void deletingTheRoutineKeepsTheSessionAndClearsItsRoutine() {
        givenAGoalWithAStep();
        PracticeRoutine routine = routines.save(PracticeRoutine.create(goal.id(),
                new RoutineDetails(null, "Shadowing", Cadence.DAILY, null, 20), 0, NOW));
        StudySession session = StudySession.create(
                new SessionDetails(goal.id(), null, routine.id(), DAY_1, 10, null), SessionSource.MANUAL, NOW);
        sessions.save(session);
        flushAndClear();

        routines.delete(routine.id());
        flushAndClear();

        StudySession reloaded = sessions.findById(session.id()).orElseThrow();
        assertThat(reloaded.routineId()).isNull();
    }

    // A failed statement aborts the PostgreSQL transaction, so each rejection gets its own test.

    @Test
    void theDatabaseRejectsAZeroMinuteSession() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert(goal.id().value(), 0, "MANUAL")).hasMessageContaining("minutes");
    }

    @Test
    void theDatabaseRejectsASessionLongerThanADay() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert(goal.id().value(), 1441, "MANUAL")).hasMessageContaining("minutes");
    }

    @Test
    void theDatabaseRejectsAnUnknownSource() {
        givenAGoalWithAStep();

        assertThatThrownBy(() -> insert(goal.id().value(), 30, "VOICE")).hasMessageContaining("source");
    }

    @Test
    void theDatabaseRejectsASessionForAnUnknownGoal() {
        assertThatThrownBy(() -> insert(UUID.randomUUID(), 30, "MANUAL")).hasMessageContaining("goal_id");
    }

    private void insert(UUID goalId, int minutes, String source) {
        entityManager.getEntityManager()
                .createNativeQuery("insert into study_session (id, goal_id, started_at, minutes, source, created_at) "
                        + "values (:id, :goal, now(), :minutes, :source, now())")
                .setParameter("id", UUID.randomUUID())
                .setParameter("goal", goalId)
                .setParameter("minutes", minutes)
                .setParameter("source", source)
                .executeUpdate();
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
