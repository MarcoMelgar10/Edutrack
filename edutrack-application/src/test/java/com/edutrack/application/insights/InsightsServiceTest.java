package com.edutrack.application.insights;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryAreaRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryStudySessionRepository;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsightsServiceTest {

    /** A Wednesday; its Monday-based week started on 2026-10-26. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Instant CREATED = Instant.parse("2026-10-01T09:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryAreaRepository areas = new InMemoryAreaRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryStudySessionRepository sessions = new InMemoryStudySessionRepository();
    private final InsightsService service = new InsightsService(goals, areas, sessions, clock);

    private final Area english = areas.save(Area.create("English", null, AreaIcon.GLOBE, 0, NOW));
    private final Area technologies = areas.save(Area.create("Technologies", null, AreaIcon.CODE, 1, NOW));
    private final Goal b2 = goals.save(Goal.create(english.id(), "Pass B2", null, null, 0, CREATED));
    private final Goal java = goals.save(Goal.create(technologies.id(), "Learn Java", null, null, 0, CREATED));

    private void studied(Goal goal, String instant, int minutes) {
        sessions.save(StudySession.create(new SessionDetails(goal.id(), null, null, Instant.parse(instant), minutes, null),
                SessionSource.MANUAL, NOW));
    }

    private static void addStep(Goal goal, String hours, StepStatus status, String completedAt) {
        goal.addStep(new StepDetails("Step", null, new BigDecimal(hours)), status,
                completedAt == null ? CREATED : Instant.parse(completedAt));
    }

    // ---------- weekly minutes ----------

    @Test
    void weeklyMinutesAreOldestFirstWithTheCurrentWeekLastAndEmptyWeeksIncluded() {
        studied(b2, "2026-10-27T08:00:00Z", 30);           // this week
        studied(b2, "2026-10-14T08:00:00Z", 45);           // two weeks back

        List<WeekMinutes> weeks = service.weeklyMinutes(4, DayOfWeek.MONDAY);

        assertThat(weeks).extracting(WeekMinutes::weekStart).containsExactly(
                LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 19),
                LocalDate.of(2026, 10, 26));
        assertThat(weeks).extracting(WeekMinutes::total).containsExactly(0, 45, 0, 30);
        assertThat(weeks.get(2).minutesByArea()).isEmpty();
    }

    @Test
    void weeklyMinutesSplitByAreaInAreaOrder() {
        studied(java, "2026-10-26T08:00:00Z", 20);
        studied(b2, "2026-10-27T08:00:00Z", 30);
        studied(b2, "2026-10-28T08:00:00Z", 15);

        WeekMinutes week = service.weeklyMinutes(1, DayOfWeek.MONDAY).getFirst();

        assertThat(week.weekStart()).isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(week.minutesByArea()).containsExactly(Map.entry(english.id(), 45), Map.entry(technologies.id(), 20));
        assertThat(week.total()).isEqualTo(65);
    }

    @Test
    void weeklyMinutesFollowTheChosenWeekStart() {
        studied(b2, "2026-10-25T20:00:00Z", 40);           // Sunday

        List<WeekMinutes> monday = service.weeklyMinutes(2, DayOfWeek.MONDAY);
        List<WeekMinutes> sunday = service.weeklyMinutes(2, DayOfWeek.SUNDAY);

        assertThat(monday).extracting(WeekMinutes::total).containsExactly(40, 0);
        assertThat(sunday).extracting(WeekMinutes::total).containsExactly(0, 40);
        assertThat(sunday.getLast().weekStart()).isEqualTo(LocalDate.of(2026, 10, 25));
    }

    @Test
    void weeklyMinutesLeaveOutSessionsBeforeTheFirstWeek() {
        studied(b2, "2026-10-04T08:00:00Z", 99);

        assertThat(service.weeklyMinutes(3, DayOfWeek.MONDAY)).extracting(WeekMinutes::total).containsExactly(0, 0, 0);
    }

    @Test
    void weeklyMinutesForNoWeeksIsEmpty() {
        assertThat(service.weeklyMinutes(0, DayOfWeek.MONDAY)).isEmpty();
    }

    // ---------- daily minutes ----------

    @Test
    void dailyMinutesAddUpPerDayWithinTheRange() {
        studied(b2, "2026-10-24T23:00:00Z", 5);            // before the range
        studied(b2, "2026-10-25T08:00:00Z", 10);
        studied(java, "2026-10-25T18:00:00Z", 15);
        studied(b2, "2026-10-28T08:00:00Z", 20);

        Map<LocalDate, Integer> daily = service.dailyMinutes(LocalDate.of(2026, 10, 25), TODAY);

        assertThat(daily).containsExactly(Map.entry(LocalDate.of(2026, 10, 25), 25), Map.entry(TODAY, 20));
    }

    @Test
    void dailyMinutesAreEmptyWithoutSessions() {
        assertThat(service.dailyMinutes(LocalDate.of(2026, 10, 1), TODAY)).isEmpty();
    }

    // ---------- goal progress history ----------

    @Test
    void progressHistoryHasAPointForTheCreationDayEachCompletionDayAndToday() {
        addStep(b2, "10", StepStatus.DONE, "2026-10-05T12:00:00Z");
        addStep(b2, "10", StepStatus.DONE, "2026-10-12T12:00:00Z");
        addStep(b2, "20", StepStatus.NOT_STARTED, null);

        List<ProgressPoint> history = service.goalProgressHistory(b2.id());

        assertThat(history).extracting(ProgressPoint::on).containsExactly(
                LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), LocalDate.of(2026, 10, 12), TODAY);
        assertThat(history).extracting(point -> point.progress().rounded()).containsExactly(0, 25, 50, 50);
        assertThat(history.getLast().progress()).isEqualTo(b2.progress());
    }

    @Test
    void stepsCompletedOnTheSameDayShareOnePoint() {
        addStep(b2, "10", StepStatus.DONE, "2026-10-05T08:00:00Z");
        addStep(b2, "10", StepStatus.DONE, "2026-10-05T18:00:00Z");
        addStep(b2, "20", StepStatus.NOT_STARTED, null);

        List<ProgressPoint> history = service.goalProgressHistory(b2.id());

        assertThat(history).extracting(ProgressPoint::on)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), TODAY);
        assertThat(history.get(1).progress().rounded()).isEqualTo(50);
    }

    @Test
    void stepsCompletedOnTheCreationDayCountInTheFirstPoint() {
        addStep(b2, "10", StepStatus.DONE, "2026-10-01T15:00:00Z");
        addStep(b2, "30", StepStatus.NOT_STARTED, null);

        List<ProgressPoint> history = service.goalProgressHistory(b2.id());

        assertThat(history.getFirst().on()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(history.getFirst().progress().rounded()).isEqualTo(25);
    }

    @Test
    void aGoalCompletedTodayEndsAt100Percent() {
        addStep(b2, "10", StepStatus.DONE, "2026-10-05T12:00:00Z");
        addStep(b2, "10", StepStatus.DONE, "2026-10-28T09:00:00Z");

        List<ProgressPoint> history = service.goalProgressHistory(b2.id());

        assertThat(history).extracting(ProgressPoint::on)
                .containsExactly(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5), TODAY);
        assertThat(history.getLast().progress()).isEqualTo(Progress.COMPLETE);
    }

    @Test
    void aGoalWithoutStepsIsFlatAtZero() {
        List<ProgressPoint> history = service.goalProgressHistory(java.id());

        assertThat(history).extracting(ProgressPoint::on).containsExactly(LocalDate.of(2026, 10, 1), TODAY);
        assertThat(history).extracting(ProgressPoint::progress).containsOnly(Progress.ZERO);
    }

    @Test
    void aGoalCreatedTodayHasASinglePoint() {
        Goal fresh = goals.save(Goal.create(english.id(), "Fresh", null, null, 1, NOW));

        assertThat(service.goalProgressHistory(fresh.id())).containsExactly(new ProgressPoint(TODAY, Progress.ZERO));
    }

    @Test
    void progressHistoryOfAnUnknownGoalIsNotFound() {
        assertThatThrownBy(() -> service.goalProgressHistory(GoalId.newId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void progressHistoryDoesNotChangeTheGoal() {
        addStep(b2, "10", StepStatus.DONE, "2026-10-05T12:00:00Z");
        addStep(b2, "10", StepStatus.NOT_STARTED, null);

        service.goalProgressHistory(b2.id());

        assertThat(b2.progress().rounded()).isEqualTo(50);
        assertThat(b2.steps().getFirst().status()).isEqualTo(StepStatus.DONE);
    }
}
