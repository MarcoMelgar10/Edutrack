package com.edutrack.application.progress;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryStudySessionRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Pace;
import com.edutrack.domain.progress.Pace.PaceStatus;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Instant TWO_MONTHS_AGO = NOW.minusSeconds(60 * 86_400L);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryStudySessionRepository sessions = new InMemoryStudySessionRepository();
    private final PaceService service = new PaceService(goals, sessions, clock);
    private final AreaId areaId = AreaId.newId();

    private Goal goal(String title, LocalDate deadline, Integer weeklyTarget) {
        return goals.save(Goal.create(areaId, title, null, deadline, weeklyTarget, goals.findAll().size(), TWO_MONTHS_AGO));
    }

    private static void step(Goal goal, String hours, StepStatus status) {
        BigDecimal estimate = hours == null ? null : new BigDecimal(hours);
        goal.addStep(new StepDetails("Step", null, estimate), status, NOW);
    }

    private void studied(Goal goal, String day, int minutes) {
        sessions.save(StudySession.create(new SessionDetails(goal.id(), null, null,
                Instant.parse(day + "T08:00:00Z"), minutes, null), SessionSource.MANUAL, NOW));
    }

    @Test
    void aGoalBehindItsDeadlineGetsTheRequiredAndActualPace() {
        Goal goal = goal("Pass B2", TODAY.plusDays(13), null);
        step(goal, "5", StepStatus.NOT_STARTED);
        step(goal, "15", StepStatus.IN_PROGRESS);
        step(goal, "30", StepStatus.DONE);
        studied(goal, "2026-10-20", 400);
        studied(goal, "2026-10-01", 500);

        GoalPace result = service.pace(goal.id());

        assertThat(result.goalId()).isEqualTo(goal.id());
        assertThat(result.areaId()).isEqualTo(areaId);
        assertThat(result.title()).isEqualTo("Pass B2");
        assertThat(result.pace()).isEqualTo(new Pace(PaceStatus.BEHIND, 600, 225, TODAY.plusDays(38)));
    }

    @Test
    void onlySessionsOfTheLast28DaysCount() {
        Goal goal = goal("Guitar", null, 100);
        studied(goal, "2026-09-30", 1000);     // 28 days ago: one day outside the window
        studied(goal, "2026-10-01", 400);      // 27 days ago: the first day inside it

        assertThat(service.pace(goal.id()).pace().actualMinutesPerWeek()).isEqualTo(100);
    }

    @Test
    void sessionsOfOtherGoalsDoNotCount() {
        Goal guitar = goal("Guitar", null, 100);
        Goal english = goal("English", null, 100);
        studied(english, "2026-10-20", 400);

        assertThat(service.pace(guitar.id()).pace().actualMinutesPerWeek()).isZero();
        assertThat(service.pace(english.id()).pace().actualMinutesPerWeek()).isEqualTo(100);
    }

    @Test
    void aGoalWithAWeeklyTargetAndNoRoadmapStillGetsAPace() {
        Goal goal = goal("Guitar", null, 150);
        studied(goal, "2026-10-20", 600);

        Pace pace = service.pace(goal.id()).pace();

        assertThat(pace.status()).isEqualTo(PaceStatus.ON_TRACK);
        assertThat(pace.requiredMinutesPerWeek()).isEqualTo(150);
    }

    @Test
    void aGoalWithoutDeadlineOrTargetHasNoPlan() {
        Goal goal = goal("Read more", null, null);
        step(goal, "10", StepStatus.NOT_STARTED);
        studied(goal, "2026-10-20", 400);

        Pace pace = service.pace(goal.id()).pace();

        assertThat(pace.status()).isEqualTo(PaceStatus.NO_PLAN);
        assertThat(pace.actualMinutesPerWeek()).isEqualTo(100);
        assertThat(pace.projectedFinish()).isEqualTo(TODAY.plusDays(42));      // 600 min at 100 min a week
    }

    @Test
    void stepsWithoutEstimatesAreIgnoredAndNoEstimatesMeansNoRemainingHours() {
        Goal goal = goal("Vague", TODAY.plusDays(30), null);
        step(goal, null, StepStatus.NOT_STARTED);
        step(goal, "0", StepStatus.NOT_STARTED);

        assertThat(PaceService.remainingHours(goal)).isNull();
        assertThat(service.pace(goal.id()).pace().status()).isEqualTo(PaceStatus.NO_PLAN);

        step(goal, "2", StepStatus.NOT_STARTED);
        assertThat(PaceService.remainingHours(goal)).isEqualByComparingTo("2");
    }

    @Test
    void whenEveryEstimatedStepIsDoneTheGoalIsFinished() {
        Goal goal = goal("Almost", TODAY.plusDays(30), null);
        step(goal, "10", StepStatus.DONE);
        step(goal, null, StepStatus.NOT_STARTED);

        assertThat(PaceService.remainingHours(goal)).isEqualByComparingTo("0");
        assertThat(service.pace(goal.id()).pace().status()).isEqualTo(PaceStatus.FINISHED);
    }

    @Test
    void aGoalCreatedYesterdayIsAveragedOverAWeek() {
        Goal fresh = goals.save(Goal.create(areaId, "New", null, null, 120, 0, NOW.minusSeconds(86_400)));
        studied(fresh, "2026-10-27", 120);

        Pace pace = service.pace(fresh.id()).pace();

        assertThat(pace.actualMinutesPerWeek()).isEqualTo(120);
        assertThat(pace.status()).isEqualTo(PaceStatus.ON_TRACK);
    }

    @Test
    void allListsEveryGoalThatIsNotCompleted() {
        Goal open = goal("Open", null, 100);
        Goal done = goal("Done", null, 100);
        step(done, "5", StepStatus.DONE);
        Goal started = goal("Started", TODAY.plusDays(10), null);
        step(started, "5", StepStatus.IN_PROGRESS);

        List<GoalPace> all = service.all();

        assertThat(all).extracting(GoalPace::goalId).containsExactly(open.id(), started.id());
        assertThat(all).extracting(GoalPace::title).containsExactly("Open", "Started");
    }

    @Test
    void allIsEmptyWithoutGoals() {
        assertThat(service.all()).isEmpty();
    }

    @Test
    void paceOfAnUnknownGoalIsNotFound() {
        assertThatThrownBy(() -> service.pace(GoalId.newId())).isInstanceOf(NotFoundException.class);
    }
}
