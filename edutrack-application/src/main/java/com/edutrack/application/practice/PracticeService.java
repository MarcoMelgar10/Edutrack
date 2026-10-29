package com.edutrack.application.practice;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.session.SessionView;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.practice.PracticeRoutineRepository;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Use cases for practice routines: open-ended things practised on a rhythm, shown on Today with
 * a one-tap way to log the time. Framework-free; wired as a bean by the desktop module.
 */
public class PracticeService {

    private final PracticeRoutineRepository routines;
    private final StudySessionRepository sessions;
    private final GoalRepository goals;
    private final Clock clock;

    public PracticeService(PracticeRoutineRepository routines, StudySessionRepository sessions,
                           GoalRepository goals, Clock clock) {
        this.routines = routines;
        this.sessions = sessions;
        this.goals = goals;
        this.clock = clock;
    }

    /** The goal's routines in list order, paused ones included. */
    public List<RoutineView> forGoal(GoalId goalId) {
        Goal goal = loadGoal(goalId);
        return routines.findByGoal(goalId).stream().map(routine -> RoutineView.of(routine, goal)).toList();
    }

    public RoutineView create(PracticeCommands.CreateRoutine command) {
        Goal goal = loadGoal(command.goalId());
        requireStepOf(goal, command.stepId());
        int position = routines.findByGoal(goal.id()).stream().mapToInt(PracticeRoutine::position).max().orElse(-1) + 1;
        PracticeRoutine routine = PracticeRoutine.create(goal.id(), command.details(), position, clock.instant());
        return RoutineView.of(routines.save(routine), goal);
    }

    public RoutineView update(PracticeCommands.UpdateRoutine command) {
        return change(command.routineId(), (routine, goal) -> {
            requireStepOf(goal, command.stepId());
            routine.update(command.details());
        });
    }

    public void delete(PracticeRoutineId id) {
        routines.delete(id);
    }

    public RoutineView pause(PracticeRoutineId id) {
        return change(id, (routine, goal) -> routine.pause());
    }

    public RoutineView resume(PracticeRoutineId id) {
        return change(id, (routine, goal) -> routine.resume());
    }

    /** Swaps the routine with its neighbour in its goal's list; moving past either end does nothing. */
    public List<RoutineView> move(PracticeRoutineId id, MoveDirection direction) {
        PracticeRoutine moved = load(id);
        List<PracticeRoutine> ordered = new ArrayList<>(routines.findByGoal(moved.goalId()));
        int from = ordered.indexOf(moved);
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (to >= 0 && to < ordered.size()) {
            Collections.swap(ordered, from, to);
            for (int position = 0; position < ordered.size(); position++) {
                PracticeRoutine routine = ordered.get(position);
                if (routine.position() != position) {
                    routine.moveTo(position);
                    routines.save(routine);
                }
            }
        }
        return forGoal(moved.goalId());
    }

    /**
     * The active routines of every goal with today's and this week's progress, the week starting on
     * {@code weekStart}. Only sessions logged against the routine count towards it.
     */
    public List<RoutineStatus> today(DayOfWeek weekStart) {
        List<PracticeRoutine> active = routines.findActive();
        if (active.isEmpty()) {
            return List.of();
        }
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        LocalDate firstDay = today.with(TemporalAdjusters.previousOrSame(weekStart));
        Map<PracticeRoutineId, Set<LocalDate>> daysByRoutine = new HashMap<>();
        Map<PracticeRoutineId, Integer> minutesToday = new HashMap<>();
        for (StudySession session : sessions.findStartedBetween(firstDay.atStartOfDay(zone).toInstant(),
                firstDay.plusDays(7).atStartOfDay(zone).toInstant())) {
            if (session.routineId() == null) {
                continue;
            }
            LocalDate day = session.startedAt().atZone(zone).toLocalDate();
            daysByRoutine.computeIfAbsent(session.routineId(), id -> new HashSet<>()).add(day);
            if (day.equals(today)) {
                minutesToday.merge(session.routineId(), session.minutes(), Integer::sum);
            }
        }

        Map<GoalId, Goal> goalsById = new HashMap<>();
        goals.findAll().forEach(goal -> goalsById.put(goal.id(), goal));
        List<RoutineStatus> statuses = new ArrayList<>();
        for (PracticeRoutine routine : active) {
            Goal goal = goalsById.get(routine.goalId());
            if (goal == null) {
                continue;
            }
            int minutes = minutesToday.getOrDefault(routine.id(), 0);
            int days = daysByRoutine.getOrDefault(routine.id(), Set.of()).size();
            statuses.add(new RoutineStatus(RoutineView.of(routine, goal), goal.title(), goal.areaId(), minutes, days,
                    minutes > 0, days >= routine.timesPerWeek()));
        }
        return statuses;
    }

    /**
     * Logs a practice that just ended: a manual session on the routine's goal and step lasting
     * {@code minutes}, starting that many minutes ago.
     */
    public SessionView logPractice(PracticeRoutineId id, Integer minutes, String note) {
        PracticeRoutine routine = load(id);
        Goal goal = loadGoal(routine.goalId());
        Instant now = clock.instant();
        Instant startedAt = minutes == null || minutes < 0 ? now : now.minus(Duration.ofMinutes(minutes));
        StudySession session = StudySession.create(new SessionDetails(routine.goalId(), routine.stepId(),
                routine.id(), startedAt, minutes, note), SessionSource.MANUAL, now);
        return SessionView.of(sessions.save(session), goal);
    }

    private RoutineView change(PracticeRoutineId id, RoutineChange change) {
        PracticeRoutine routine = load(id);
        Goal goal = loadGoal(routine.goalId());
        change.apply(routine, goal);
        return RoutineView.of(routines.save(routine), goal);
    }

    @FunctionalInterface
    private interface RoutineChange {
        void apply(PracticeRoutine routine, Goal goal);
    }

    private PracticeRoutine load(PracticeRoutineId id) {
        return routines.findById(id).orElseThrow(() -> new NotFoundException("Routine", id));
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
