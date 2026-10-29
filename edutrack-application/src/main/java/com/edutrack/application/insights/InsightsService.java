package com.edutrack.application.insights;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.ProgressCalculator;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/** The numbers behind the Insights charts. Framework-free; wired as a bean by the desktop module. */
public class InsightsService {

    private final GoalRepository goals;
    private final AreaRepository areas;
    private final StudySessionRepository sessions;
    private final Clock clock;

    public InsightsService(GoalRepository goals, AreaRepository areas, StudySessionRepository sessions, Clock clock) {
        this.goals = goals;
        this.areas = areas;
        this.sessions = sessions;
        this.clock = clock;
    }

    /**
     * Minutes per week and area for the last {@code weeks} weeks (the week starting on
     * {@code weekStart}), oldest first, the current week last. Weeks without study are included.
     */
    public List<WeekMinutes> weeklyMinutes(int weeks, DayOfWeek weekStart) {
        if (weeks < 1) {
            return List.of();
        }
        LocalDate currentWeek = LocalDate.now(clock).with(TemporalAdjusters.previousOrSame(weekStart));
        LocalDate firstWeek = currentWeek.minusWeeks(weeks - 1L);

        Map<GoalId, AreaId> areaOfGoal = new HashMap<>();
        goals.findAll().forEach(goal -> areaOfGoal.put(goal.id(), goal.areaId()));
        Map<LocalDate, Map<AreaId, Integer>> byWeek = new HashMap<>();
        for (StudySession session : sessionsBetween(firstWeek, currentWeek.plusDays(6))) {
            AreaId area = areaOfGoal.get(session.goalId());
            if (area == null) {
                continue;
            }
            LocalDate week = dayOf(session).with(TemporalAdjusters.previousOrSame(weekStart));
            byWeek.computeIfAbsent(week, key -> new HashMap<>()).merge(area, session.minutes(), Integer::sum);
        }

        List<AreaId> areaOrder = areas.findAll().stream().map(Area::id).toList();
        List<WeekMinutes> result = new ArrayList<>(weeks);
        for (LocalDate week = firstWeek; !week.isAfter(currentWeek); week = week.plusWeeks(1)) {
            Map<AreaId, Integer> minutes = byWeek.getOrDefault(week, Map.of());
            Map<AreaId, Integer> ordered = new LinkedHashMap<>();
            areaOrder.stream().filter(minutes::containsKey).forEach(area -> ordered.put(area, minutes.get(area)));
            result.add(new WeekMinutes(week, ordered, ordered.values().stream().mapToInt(Integer::intValue).sum()));
        }
        return result;
    }

    /** Minutes per day for the days from {@code from} to {@code to}, both included; days without study are absent. */
    public Map<LocalDate, Integer> dailyMinutes(LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> minutes = new TreeMap<>();
        for (StudySession session : sessionsBetween(from, to)) {
            minutes.merge(dayOf(session), session.minutes(), Integer::sum);
        }
        return minutes;
    }

    /**
     * How the goal's progress grew: a point on the day it was created, one on each day a (currently
     * done) step was completed, and a last one today. Each point is the progress the goal had with
     * only the steps completed up to that day counted as done, using today's estimates.
     */
    public List<ProgressPoint> goalProgressHistory(GoalId goalId) {
        Goal goal = goals.findById(goalId).orElseThrow(() -> new NotFoundException("Goal", goalId));
        ZoneId zone = clock.getZone();
        LocalDate created = goal.createdAt().atZone(zone).toLocalDate();
        LocalDate today = LocalDate.now(clock);

        Map<RoadmapStep, LocalDate> completedOn = new HashMap<>();
        TreeSet<LocalDate> days = new TreeSet<>();
        days.add(created);
        for (RoadmapStep step : goal.steps()) {
            if (step.status() == StepStatus.DONE && step.completedAt() != null) {
                LocalDate day = step.completedAt().atZone(zone).toLocalDate();
                day = day.isBefore(created) ? created : day;
                completedOn.put(step, day);
                days.add(day);
            }
        }
        days.add(today.isBefore(created) ? created : today);

        List<ProgressPoint> points = new ArrayList<>();
        for (LocalDate day : days) {
            points.add(new ProgressPoint(day, progressOn(goal, completedOn, day)));
        }
        return points;
    }

    private static Progress progressOn(Goal goal, Map<RoadmapStep, LocalDate> completedOn, LocalDate day) {
        List<RoadmapStep> asOfThen = goal.steps().stream()
                .map(step -> {
                    LocalDate completed = completedOn.get(step);
                    boolean done = completed != null && !completed.isAfter(day);
                    return RoadmapStep.restore(step.id(), step.title(), step.description(), step.estimatedHours(),
                            step.plannedFor(), done ? StepStatus.DONE : StepStatus.NOT_STARTED,
                            done ? step.completedAt() : null);
                })
                .toList();
        return ProgressCalculator.ofSteps(asOfThen);
    }

    private List<StudySession> sessionsBetween(LocalDate from, LocalDate to) {
        ZoneId zone = clock.getZone();
        return sessions.findStartedBetween(from.atStartOfDay(zone).toInstant(), to.plusDays(1).atStartOfDay(zone).toInstant());
    }

    private LocalDate dayOf(StudySession session) {
        return session.startedAt().atZone(clock.getZone()).toLocalDate();
    }
}
