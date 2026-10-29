package com.edutrack.application.session;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryStudySessionRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySessionId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionServiceTest {

    /** A Wednesday. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryStudySessionRepository sessions = new InMemoryStudySessionRepository();
    private final SessionService service = new SessionService(sessions, goals, clock);

    private final Goal goal = goals.save(Goal.create(AreaId.newId(), "Pass B2", null, null, 0, NOW));
    private final StepId grammar = goal.addStep(new StepDetails("Grammar", null, null), StepStatus.NOT_STARTED, NOW).id();
    private final StepId listening = goal.addStep(new StepDetails("Listening", null, null), StepStatus.NOT_STARTED, NOW).id();

    private SessionView log(StepId stepId, Instant startedAt, int minutes) {
        return service.log(new SessionCommands.LogSession(goal.id(), stepId, null, startedAt, minutes, null, null));
    }

    private static Instant daysAgo(int days) {
        return NOW.minusSeconds(days * 86_400L);
    }

    @Test
    void logSavesTheSessionAndResolvesTheStepTitle() {
        PracticeRoutineId routineId = PracticeRoutineId.newId();

        SessionView logged = service.log(new SessionCommands.LogSession(goal.id(), grammar, routineId,
                NOW.minusSeconds(3600), 45, "  Unit 3  ", SessionSource.TIMER));

        assertThat(logged.goalId()).isEqualTo(goal.id());
        assertThat(logged.stepId()).isEqualTo(grammar);
        assertThat(logged.stepTitle()).isEqualTo("Grammar");
        assertThat(logged.routineId()).isEqualTo(routineId);
        assertThat(logged.startedAt()).isEqualTo(NOW.minusSeconds(3600));
        assertThat(logged.minutes()).isEqualTo(45);
        assertThat(logged.note()).isEqualTo("Unit 3");
        assertThat(logged.source()).isEqualTo(SessionSource.TIMER);
        assertThat(sessions.findById(logged.id())).isPresent();
    }

    @Test
    void aSessionWithoutStepOrSourceIsManualAndHasNoStepTitle() {
        SessionView logged = log(null, NOW, 30);

        assertThat(logged.stepId()).isNull();
        assertThat(logged.stepTitle()).isNull();
        assertThat(logged.source()).isEqualTo(SessionSource.MANUAL);
    }

    @Test
    void logOnAnUnknownGoalIsNotFound() {
        assertThatThrownBy(() -> service.log(new SessionCommands.LogSession(GoalId.newId(), null, null, NOW, 30, null, null)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Goal not found");
        assertThat(sessions.size()).isZero();
    }

    @Test
    void logOnAStepOfAnotherGoalIsNotFound() {
        Goal other = goals.save(Goal.create(AreaId.newId(), "Other", null, null, 1, NOW));
        StepId foreign = other.addStep(new StepDetails("Foreign", null, null), StepStatus.NOT_STARTED, NOW).id();

        assertThatThrownBy(() -> log(foreign, NOW, 30))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Step not found");
        assertThat(sessions.size()).isZero();
    }

    @Test
    void logRejectsInvalidSessionsWithoutSavingThem() {
        assertThatThrownBy(() -> log(null, NOW.plusSeconds(60), 30))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("future-date"));
        assertThatThrownBy(() -> log(null, NOW, 0))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("out-of-range"));
        assertThat(sessions.size()).isZero();
    }

    @Test
    void updateCorrectsTheSessionButKeepsItsGoalRoutineAndSource() {
        PracticeRoutineId routineId = PracticeRoutineId.newId();
        SessionView logged = service.log(new SessionCommands.LogSession(goal.id(), null, routineId,
                daysAgo(1), 30, null, SessionSource.TIMER));

        SessionView updated = service.update(new SessionCommands.UpdateSession(logged.id(), listening,
                daysAgo(2), 50, "Late entry"));

        assertThat(updated.id()).isEqualTo(logged.id());
        assertThat(updated.goalId()).isEqualTo(goal.id());
        assertThat(updated.stepTitle()).isEqualTo("Listening");
        assertThat(updated.startedAt()).isEqualTo(daysAgo(2));
        assertThat(updated.minutes()).isEqualTo(50);
        assertThat(updated.note()).isEqualTo("Late entry");
        assertThat(updated.routineId()).isEqualTo(routineId);
        assertThat(updated.source()).isEqualTo(SessionSource.TIMER);
        assertThat(sessions.size()).isEqualTo(1);
    }

    @Test
    void updateOfAnUnknownSessionOrForeignStepIsNotFound() {
        SessionView logged = log(null, NOW, 30);

        assertThatThrownBy(() -> service.update(new SessionCommands.UpdateSession(
                StudySessionId.newId(), null, NOW, 30, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(new SessionCommands.UpdateSession(logged.id(), StepId.newId(), NOW, 30, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteRemovesTheSession() {
        SessionView logged = log(null, NOW, 30);

        service.delete(logged.id());

        assertThat(sessions.size()).isZero();
        assertThat(service.forGoal(goal.id())).isEmpty();
    }

    @Test
    void forGoalListsTheSessionsNewestFirstWithStepTitles() {
        log(grammar, daysAgo(3), 10);
        log(null, daysAgo(1), 20);
        log(listening, daysAgo(2), 30);

        List<SessionView> views = service.forGoal(goal.id());

        assertThat(views).extracting(SessionView::minutes).containsExactly(20, 30, 10);
        assertThat(views).extracting(SessionView::stepTitle).containsExactly(null, "Listening", "Grammar");
    }

    @Test
    void forGoalOfAnUnknownGoalIsNotFound() {
        assertThatThrownBy(() -> service.forGoal(GoalId.newId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void forStepListsOnlyThatStepsSessionsNewestFirst() {
        log(grammar, daysAgo(3), 10);
        log(listening, daysAgo(2), 30);
        log(grammar, daysAgo(1), 20);

        List<SessionView> views = service.forStep(grammar);

        assertThat(views).extracting(SessionView::minutes).containsExactly(20, 10);
        assertThat(views).extracting(SessionView::stepTitle).containsOnly("Grammar");
        assertThat(service.forStep(StepId.newId())).isEmpty();
    }

    @Test
    void totalsAddUpTheGoalEachStepAndTheLastSevenDays() {
        log(grammar, daysAgo(0), 10);
        log(grammar, daysAgo(6), 20);       // the 7th day back, today included: still counts
        log(listening, daysAgo(7), 40);     // one day too old for the week
        log(null, daysAgo(30), 80);

        SessionTotals totals = service.totals(goal.id());

        assertThat(totals.goalMinutes()).isEqualTo(150);
        assertThat(totals.stepMinutes()).containsOnly(Map.entry(grammar, 30), Map.entry(listening, 40));
        assertThat(totals.last7DaysMinutes()).isEqualTo(30);
    }

    @Test
    void totalsOfAGoalWithoutSessionsAreZero() {
        SessionTotals totals = service.totals(goal.id());

        assertThat(totals.goalMinutes()).isZero();
        assertThat(totals.stepMinutes()).isEmpty();
        assertThat(totals.last7DaysMinutes()).isZero();
    }

    @Test
    void minutesBetweenGroupsByGoalAndIncludesBothEndDays() {
        Goal other = goals.save(Goal.create(AreaId.newId(), "Other", null, null, 1, NOW));
        log(null, Instant.parse("2026-10-25T23:59:00Z"), 5);     // day before the range
        log(null, Instant.parse("2026-10-26T00:00:00Z"), 10);    // first day, first instant
        log(null, Instant.parse("2026-10-28T09:00:00Z"), 20);    // last day
        service.log(new SessionCommands.LogSession(other.id(), null, null,
                Instant.parse("2026-10-27T12:00:00Z"), 7, null, null));

        Map<GoalId, Integer> minutes = service.minutesBetween(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 28));

        assertThat(minutes).containsOnly(Map.entry(goal.id(), 30), Map.entry(other.id(), 7));
    }

    @Test
    void daysFollowTheClockZone() {
        Clock tokyo = Clock.fixed(NOW, ZoneOffset.ofHours(9));
        SessionService inTokyo = new SessionService(sessions, goals, tokyo);
        // 20:00 UTC on the 27th is already 05:00 on the 28th in Tokyo.
        inTokyo.log(new SessionCommands.LogSession(goal.id(), null, null,
                Instant.parse("2026-10-27T20:00:00Z"), 30, null, null));

        assertThat(inTokyo.minutesBetween(LocalDate.of(2026, 10, 28), LocalDate.of(2026, 10, 28)))
                .containsOnly(Map.entry(goal.id(), 30));
        assertThat(inTokyo.minutesBetween(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 27))).isEmpty();
        assertThat(inTokyo.asStudyDaySource().studyDays(LocalDate.of(2026, 10, 27), LocalDate.of(2026, 10, 28)))
                .containsExactly(LocalDate.of(2026, 10, 28));
    }

    @Test
    void theStudyDaySourceReturnsTheDaysWithASessionInRange() {
        log(null, Instant.parse("2026-10-24T08:00:00Z"), 10);
        log(null, Instant.parse("2026-10-26T08:00:00Z"), 10);
        log(grammar, Instant.parse("2026-10-26T19:00:00Z"), 10);
        log(null, Instant.parse("2026-10-27T08:00:00Z"), 10);

        Set<LocalDate> days = service.asStudyDaySource().studyDays(LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 28));

        assertThat(days).containsExactlyInAnyOrder(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27));
    }
}
