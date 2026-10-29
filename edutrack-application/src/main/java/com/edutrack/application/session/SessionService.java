package com.edutrack.application.session;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.progress.StudyDaySource;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;
import com.edutrack.domain.session.StudySessionRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Use cases for logging study time on goals and steps. Framework-free; wired as a bean by the desktop module. */
public class SessionService {

    private final StudySessionRepository sessions;
    private final GoalRepository goals;
    private final Clock clock;

    public SessionService(StudySessionRepository sessions, GoalRepository goals, Clock clock) {
        this.sessions = sessions;
        this.goals = goals;
        this.clock = clock;
    }

    /** The goal must exist and the step, if any, must be one of its steps. */
    public SessionView log(SessionCommands.LogSession command) {
        Goal goal = loadGoal(command.goalId());
        requireStepOf(goal, command.stepId());
        StudySession session = StudySession.create(new SessionDetails(command.goalId(), command.stepId(),
                command.routineId(), command.startedAt(), command.minutes(), command.note()),
                command.source(), clock.instant());
        return SessionView.of(sessions.save(session), goal);
    }

    public SessionView update(SessionCommands.UpdateSession command) {
        StudySession session = load(command.sessionId());
        Goal goal = loadGoal(session.goalId());
        requireStepOf(goal, command.stepId());
        session.update(new SessionDetails(session.goalId(), command.stepId(), session.routineId(),
                command.startedAt(), command.minutes(), command.note()), clock.instant());
        return SessionView.of(sessions.save(session), goal);
    }

    public void delete(StudySessionId id) {
        sessions.delete(id);
    }

    /** The goal's sessions, newest first. */
    public List<SessionView> forGoal(GoalId goalId) {
        Goal goal = loadGoal(goalId);
        return sessions.findByGoal(goalId).stream().map(session -> SessionView.of(session, goal)).toList();
    }

    /** The step's sessions, newest first. */
    public List<SessionView> forStep(StepId stepId) {
        Map<GoalId, Goal> owners = new HashMap<>();
        return sessions.findByStep(stepId).stream()
                .map(session -> SessionView.of(session,
                        owners.computeIfAbsent(session.goalId(), id -> goals.findById(id).orElse(null))))
                .toList();
    }

    public SessionTotals totals(GoalId goalId) {
        LocalDate firstOfLast7Days = LocalDate.now(clock).minusDays(6);
        int goalMinutes = 0;
        int last7Days = 0;
        Map<StepId, Integer> byStep = new HashMap<>();
        for (StudySession session : sessions.findByGoal(goalId)) {
            goalMinutes += session.minutes();
            if (!dayOf(session).isBefore(firstOfLast7Days)) {
                last7Days += session.minutes();
            }
            if (session.stepId() != null) {
                byStep.merge(session.stepId(), session.minutes(), Integer::sum);
            }
        }
        return new SessionTotals(goalMinutes, byStep, last7Days);
    }

    /** Minutes per goal for the days from {@code from} to {@code to}, both included; goals without sessions are absent. */
    public Map<GoalId, Integer> minutesBetween(LocalDate from, LocalDate to) {
        return sessionsBetween(from, to).stream()
                .collect(Collectors.groupingBy(StudySession::goalId, Collectors.summingInt(StudySession::minutes)));
    }

    /** Every day with a logged session counts as a study day. */
    public StudyDaySource asStudyDaySource() {
        return (from, to) -> sessionsBetween(from, to).stream().map(this::dayOf).collect(Collectors.toSet());
    }

    private List<StudySession> sessionsBetween(LocalDate from, LocalDate to) {
        ZoneId zone = clock.getZone();
        return sessions.findStartedBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private LocalDate dayOf(StudySession session) {
        return session.startedAt().atZone(clock.getZone()).toLocalDate();
    }

    private StudySession load(StudySessionId id) {
        return sessions.findById(id).orElseThrow(() -> new NotFoundException("Session", id));
    }

    private Goal loadGoal(GoalId id) {
        return goals.findById(id).orElseThrow(() -> new NotFoundException("Goal", id));
    }

    private static void requireStepOf(Goal goal, StepId stepId) {
        if (stepId != null && goal.steps().stream().map(RoadmapStep::id).noneMatch(stepId::equals)) {
            throw new NotFoundException("Step", stepId);
        }
    }
}
