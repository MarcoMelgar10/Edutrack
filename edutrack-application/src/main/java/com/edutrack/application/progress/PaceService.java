package com.edutrack.application.progress;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Pace;
import com.edutrack.domain.progress.PaceCalculator;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionRepository;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gathers what {@link PaceCalculator} needs for a goal: the estimated hours still to do and the
 * minutes studied over the last {@value PaceCalculator#WINDOW_DAYS} days. Framework-free; wired as
 * a bean by the desktop module.
 */
public class PaceService {

    private final GoalRepository goals;
    private final StudySessionRepository sessions;
    private final Clock clock;

    public PaceService(GoalRepository goals, StudySessionRepository sessions, Clock clock) {
        this.goals = goals;
        this.sessions = sessions;
        this.clock = clock;
    }

    public GoalPace pace(GoalId goalId) {
        Goal goal = goals.findById(goalId).orElseThrow(() -> new NotFoundException("Goal", goalId));
        return paceOf(goal, minutesInWindow().getOrDefault(goalId, 0));
    }

    /** The pace of every goal that is not completed yet, in goal order. */
    public List<GoalPace> all() {
        Map<GoalId, Integer> minutes = minutesInWindow();
        return goals.findAll().stream()
                .filter(goal -> !goal.isCompleted())
                .map(goal -> paceOf(goal, minutes.getOrDefault(goal.id(), 0)))
                .toList();
    }

    private GoalPace paceOf(Goal goal, int minutesInWindow) {
        ZoneId zone = clock.getZone();
        Pace pace = PaceCalculator.of(remainingHours(goal), goal.deadline(), goal.weeklyTargetMinutes(),
                minutesInWindow, goal.createdAt().atZone(zone).toLocalDate(), LocalDate.now(clock));
        return new GoalPace(goal.id(), goal.areaId(), goal.title(), pace);
    }

    /**
     * Estimated hours of the steps not done; {@code null} when no step of the goal has an estimate
     * (a goal with estimates but nothing left to do has 0 hours remaining).
     */
    static BigDecimal remainingHours(Goal goal) {
        boolean anyEstimate = goal.steps().stream().anyMatch(PaceService::isEstimated);
        if (!anyEstimate) {
            return null;
        }
        return goal.steps().stream()
                .filter(step -> step.status() != StepStatus.DONE && isEstimated(step))
                .map(RoadmapStep::estimatedHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static boolean isEstimated(RoadmapStep step) {
        return step.estimatedHours() != null && step.estimatedHours().signum() != 0;
    }

    /** Minutes per goal over the last 28 days, today included. */
    private Map<GoalId, Integer> minutesInWindow() {
        ZoneId zone = clock.getZone();
        LocalDate today = LocalDate.now(clock);
        Map<GoalId, Integer> minutes = new HashMap<>();
        for (StudySession session : sessions.findStartedBetween(
                today.minusDays(PaceCalculator.WINDOW_DAYS - 1L).atStartOfDay(zone).toInstant(),
                today.plusDays(1).atStartOfDay(zone).toInstant())) {
            minutes.merge(session.goalId(), session.minutes(), Integer::sum);
        }
        return minutes;
    }
}
