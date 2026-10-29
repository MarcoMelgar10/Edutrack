package com.edutrack.application.practice;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.session.SessionView;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryPracticeRoutineRepository;
import com.edutrack.application.support.InMemoryStudySessionRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeServiceTest {

    /** A Wednesday; its Monday-based week started on 2026-10-26. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryPracticeRoutineRepository routines = new InMemoryPracticeRoutineRepository();
    private final InMemoryStudySessionRepository sessions = new InMemoryStudySessionRepository();
    private final PracticeService service = new PracticeService(routines, sessions, goals, clock);

    private final AreaId areaId = AreaId.newId();
    private final Goal goal = goals.save(Goal.create(areaId, "Speak fluently", null, null, 0, NOW));
    private final StepId speaking = goal.addStep(new StepDetails("Speaking", null, null), StepStatus.NOT_STARTED, NOW).id();

    private RoutineView create(String title, Cadence cadence, Integer times, Integer minutes) {
        return service.create(new PracticeCommands.CreateRoutine(goal.id(), null, title, cadence, times, minutes));
    }

    private void practiceAt(RoutineView routine, String instant, int minutes) {
        sessions.save(StudySession.create(new SessionDetails(goal.id(), routine.stepId(), routine.id(),
                Instant.parse(instant), minutes, null), SessionSource.MANUAL, NOW));
    }

    private List<String> titles(List<RoutineView> views) {
        return views.stream().map(RoutineView::title).toList();
    }

    @Test
    void createAddsTheRoutineAtTheEndOfTheGoalsList() {
        RoutineView first = create("Shadowing", Cadence.WEEKLY, 5, 20);
        RoutineView second = create("Vocabulary", Cadence.DAILY, null, 15);

        assertThat(first.position()).isZero();
        assertThat(second.position()).isEqualTo(1);
        assertThat(second.timesPerWeek()).isEqualTo(7);
        assertThat(first.active()).isTrue();
        assertThat(first.goalId()).isEqualTo(goal.id());
        assertThat(titles(service.forGoal(goal.id()))).containsExactly("Shadowing", "Vocabulary");
    }

    @Test
    void createResolvesTheStepTitle() {
        RoutineView view = service.create(new PracticeCommands.CreateRoutine(goal.id(), speaking, "Shadowing",
                Cadence.DAILY, null, 20));

        assertThat(view.stepId()).isEqualTo(speaking);
        assertThat(view.stepTitle()).isEqualTo("Speaking");
        assertThat(create("No step", Cadence.DAILY, null, 20).stepTitle()).isNull();
    }

    @Test
    void createOnAnUnknownGoalOrForeignStepIsNotFound() {
        assertThatThrownBy(() -> service.create(new PracticeCommands.CreateRoutine(
                GoalId.newId(), null, "X", Cadence.DAILY, null, 20)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.create(new PracticeCommands.CreateRoutine(
                goal.id(), StepId.newId(), "X", Cadence.DAILY, null, 20)))
                .isInstanceOf(NotFoundException.class);
        assertThat(routines.findByGoal(goal.id())).isEmpty();
    }

    @Test
    void createRejectsInvalidRoutines() {
        assertThatThrownBy(() -> create("  ", Cadence.DAILY, null, 20)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> create("X", Cadence.WEEKLY, 9, 20)).isInstanceOf(DomainValidationException.class);
        assertThat(routines.findByGoal(goal.id())).isEmpty();
    }

    @Test
    void updateChangesTheRoutine() {
        RoutineView created = create("Shadowing", Cadence.DAILY, null, 20);

        RoutineView updated = service.update(new PracticeCommands.UpdateRoutine(created.id(), speaking,
                "Podcasts", Cadence.WEEKLY, 3, 30));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.title()).isEqualTo("Podcasts");
        assertThat(updated.cadence()).isEqualTo(Cadence.WEEKLY);
        assertThat(updated.timesPerWeek()).isEqualTo(3);
        assertThat(updated.targetMinutes()).isEqualTo(30);
        assertThat(updated.stepTitle()).isEqualTo("Speaking");
    }

    @Test
    void updateWithAForeignStepOrUnknownRoutineIsNotFoundAndChangesNothing() {
        RoutineView created = create("Shadowing", Cadence.DAILY, null, 20);

        assertThatThrownBy(() -> service.update(new PracticeCommands.UpdateRoutine(created.id(), StepId.newId(),
                "Podcasts", Cadence.DAILY, null, 30))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(new PracticeCommands.UpdateRoutine(PracticeRoutineId.newId(), null,
                "Podcasts", Cadence.DAILY, null, 30))).isInstanceOf(NotFoundException.class);

        assertThat(service.forGoal(goal.id()).getFirst().title()).isEqualTo("Shadowing");
    }

    @Test
    void pauseAndResumeToggleTheRoutine() {
        RoutineView created = create("Shadowing", Cadence.DAILY, null, 20);

        assertThat(service.pause(created.id()).active()).isFalse();
        assertThat(service.forGoal(goal.id()).getFirst().active()).isFalse();
        assertThat(service.resume(created.id()).active()).isTrue();
        assertThat(service.forGoal(goal.id()).getFirst().active()).isTrue();
    }

    @Test
    void deleteRemovesTheRoutine() {
        RoutineView created = create("Shadowing", Cadence.DAILY, null, 20);

        service.delete(created.id());

        assertThat(service.forGoal(goal.id())).isEmpty();
    }

    @Test
    void moveSwapsWithTheNeighbourAndRenumbers() {
        RoutineView first = create("First", Cadence.DAILY, null, 10);
        create("Second", Cadence.DAILY, null, 10);
        RoutineView third = create("Third", Cadence.DAILY, null, 10);

        assertThat(titles(service.move(first.id(), MoveDirection.DOWN))).containsExactly("Second", "First", "Third");
        assertThat(titles(service.move(third.id(), MoveDirection.UP))).containsExactly("Second", "Third", "First");
        assertThat(service.forGoal(goal.id())).extracting(RoutineView::position).containsExactly(0, 1, 2);
    }

    @Test
    void movingPastEitherEndDoesNothing() {
        RoutineView first = create("First", Cadence.DAILY, null, 10);
        RoutineView second = create("Second", Cadence.DAILY, null, 10);

        assertThat(titles(service.move(first.id(), MoveDirection.UP))).containsExactly("First", "Second");
        assertThat(titles(service.move(second.id(), MoveDirection.DOWN))).containsExactly("First", "Second");
    }

    @Test
    void moveRenumbersGapsLeftByDeletedRoutines() {
        RoutineView first = create("First", Cadence.DAILY, null, 10);
        RoutineView second = create("Second", Cadence.DAILY, null, 10);
        create("Third", Cadence.DAILY, null, 10);
        service.delete(second.id());

        service.move(first.id(), MoveDirection.DOWN);

        assertThat(service.forGoal(goal.id())).extracting(RoutineView::title).containsExactly("Third", "First");
        assertThat(service.forGoal(goal.id())).extracting(RoutineView::position).containsExactly(0, 1);
    }

    @Test
    void todayShowsOnlyActiveRoutinesWithTheirGoalAndArea() {
        RoutineView active = create("Shadowing", Cadence.DAILY, null, 20);
        RoutineView paused = create("Vocabulary", Cadence.DAILY, null, 20);
        service.pause(paused.id());

        List<RoutineStatus> today = service.today(DayOfWeek.MONDAY);

        assertThat(today).hasSize(1);
        RoutineStatus status = today.getFirst();
        assertThat(status.routine().id()).isEqualTo(active.id());
        assertThat(status.goalTitle()).isEqualTo("Speak fluently");
        assertThat(status.areaId()).isEqualTo(areaId);
        assertThat(status.minutesToday()).isZero();
        assertThat(status.sessionsThisWeek()).isZero();
        assertThat(status.doneToday()).isFalse();
        assertThat(status.metThisWeek()).isFalse();
    }

    @Test
    void todayCountsTodaysMinutesAndMarksTheRoutineDone() {
        RoutineView routine = create("Shadowing", Cadence.WEEKLY, 3, 20);
        practiceAt(routine, "2026-10-28T06:00:00Z", 15);
        practiceAt(routine, "2026-10-28T08:00:00Z", 10);

        RoutineStatus status = service.today(DayOfWeek.MONDAY).getFirst();

        assertThat(status.minutesToday()).isEqualTo(25);
        assertThat(status.doneToday()).isTrue();
        assertThat(status.sessionsThisWeek()).isEqualTo(1);   // two sessions on one day count once
        assertThat(status.metThisWeek()).isFalse();
    }

    @Test
    void theWeekIsMetWhenEnoughDaysHaveASession() {
        RoutineView routine = create("Shadowing", Cadence.WEEKLY, 3, 20);
        practiceAt(routine, "2026-10-26T18:00:00Z", 20);
        practiceAt(routine, "2026-10-27T18:00:00Z", 20);
        assertThat(service.today(DayOfWeek.MONDAY).getFirst().metThisWeek()).isFalse();

        practiceAt(routine, "2026-10-28T07:00:00Z", 20);

        RoutineStatus status = service.today(DayOfWeek.MONDAY).getFirst();
        assertThat(status.sessionsThisWeek()).isEqualTo(3);
        assertThat(status.metThisWeek()).isTrue();
    }

    @Test
    void sessionsOfEarlierWeeksOtherRoutinesAndNoRoutineDoNotCount() {
        RoutineView routine = create("Shadowing", Cadence.WEEKLY, 1, 20);
        RoutineView other = create("Vocabulary", Cadence.WEEKLY, 1, 20);
        practiceAt(routine, "2026-10-25T23:00:00Z", 20);                  // Sunday: previous week
        practiceAt(other, "2026-10-27T10:00:00Z", 20);
        sessions.save(StudySession.create(new SessionDetails(goal.id(), null, null,
                Instant.parse("2026-10-28T08:00:00Z"), 20, null), SessionSource.MANUAL, NOW));

        List<RoutineStatus> today = service.today(DayOfWeek.MONDAY);

        assertThat(today.get(0).sessionsThisWeek()).isZero();
        assertThat(today.get(0).minutesToday()).isZero();
        assertThat(today.get(1).sessionsThisWeek()).isEqualTo(1);
    }

    @Test
    void theWeekStartDecidesWhichSessionsCount() {
        RoutineView routine = create("Shadowing", Cadence.WEEKLY, 1, 20);
        practiceAt(routine, "2026-10-25T12:00:00Z", 20);                  // Sunday

        assertThat(service.today(DayOfWeek.MONDAY).getFirst().sessionsThisWeek()).isZero();
        assertThat(service.today(DayOfWeek.SUNDAY).getFirst().sessionsThisWeek()).isEqualTo(1);
    }

    @Test
    void todayIsEmptyWithoutRoutines() {
        assertThat(service.today(DayOfWeek.MONDAY)).isEmpty();
    }

    @Test
    void logPracticeCreatesAManualSessionEndingNow() {
        RoutineView routine = service.create(new PracticeCommands.CreateRoutine(goal.id(), speaking, "Shadowing",
                Cadence.DAILY, null, 20));

        SessionView logged = service.logPractice(routine.id(), 25, "  Felt good ");

        assertThat(logged.goalId()).isEqualTo(goal.id());
        assertThat(logged.stepId()).isEqualTo(speaking);
        assertThat(logged.stepTitle()).isEqualTo("Speaking");
        assertThat(logged.routineId()).isEqualTo(routine.id());
        assertThat(logged.minutes()).isEqualTo(25);
        assertThat(logged.note()).isEqualTo("Felt good");
        assertThat(logged.source()).isEqualTo(SessionSource.MANUAL);
        assertThat(logged.startedAt()).isEqualTo(NOW.minusSeconds(25 * 60));
        assertThat(sessions.size()).isEqualTo(1);
    }

    @Test
    void loggedPracticeShowsUpOnToday() {
        RoutineView routine = create("Shadowing", Cadence.DAILY, null, 20);

        service.logPractice(routine.id(), 20, null);

        RoutineStatus status = service.today(DayOfWeek.MONDAY).getFirst();
        assertThat(status.minutesToday()).isEqualTo(20);
        assertThat(status.doneToday()).isTrue();
    }

    @Test
    void logPracticeRejectsInvalidMinutesAndUnknownRoutines() {
        RoutineView routine = create("Shadowing", Cadence.DAILY, null, 20);

        assertThatThrownBy(() -> service.logPractice(routine.id(), null, null)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.logPractice(routine.id(), 0, null)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.logPractice(routine.id(), 5000, null)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.logPractice(PracticeRoutineId.newId(), 20, null)).isInstanceOf(NotFoundException.class);
        assertThat(sessions.size()).isZero();
    }
}
