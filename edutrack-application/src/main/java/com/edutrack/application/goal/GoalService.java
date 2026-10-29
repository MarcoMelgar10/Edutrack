package com.edutrack.application.goal;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.LearningRecordRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

/**
 * Use cases for goals and their roadmaps. Every change returns the refreshed {@link GoalDetails},
 * so the UI can redraw the goal panel from a single result.
 */
public class GoalService {

    private final GoalRepository goals;
    private final AreaRepository areas;
    private final LearningRecordRepository records;
    private final Clock clock;

    public GoalService(GoalRepository goals, AreaRepository areas, LearningRecordRepository records, Clock clock) {
        this.goals = goals;
        this.areas = areas;
        this.records = records;
        this.clock = clock;
    }

    /** Goals of an area, in card order. */
    public List<GoalSummary> listGoals(AreaId areaId) {
        return goals.findByArea(areaId).stream().map(GoalSummary::of).toList();
    }

    public GoalDetails getGoal(GoalId id) {
        return details(load(id));
    }

    public GoalDetails createGoal(GoalCommands.CreateGoal command) {
        if (areas.findById(command.areaId()).isEmpty()) {
            throw new NotFoundException("Area", command.areaId());
        }
        int position = goals.findByArea(command.areaId()).stream().mapToInt(Goal::position).max().orElse(-1) + 1;
        Goal goal = Goal.create(command.areaId(), command.title(), command.description(), command.deadline(),
                command.weeklyTargetMinutes(), position, clock.instant());
        return details(goals.save(goal));
    }

    public GoalDetails updateGoal(GoalCommands.UpdateGoal command) {
        return change(command.goalId(), goal -> goal.update(command.title(), command.description(),
                command.deadline(), command.weeklyTargetMinutes()));
    }

    /** Swaps the goal with its neighbour in its area's list; moving past either end does nothing. */
    public List<GoalSummary> moveGoal(GoalId id, MoveDirection direction) {
        Goal moved = load(id);
        List<Goal> ordered = new ArrayList<>(goals.findByArea(moved.areaId()));
        int from = ordered.indexOf(moved);
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (to >= 0 && to < ordered.size()) {
            Collections.swap(ordered, from, to);
            for (int position = 0; position < ordered.size(); position++) {
                Goal goal = ordered.get(position);
                if (goal.position() != position) {
                    goal.moveTo(position);
                    goals.save(goal);
                }
            }
        }
        return listGoals(moved.areaId());
    }

    /** Deletes the goal with its whole roadmap. */
    public void deleteGoal(GoalId id) {
        goals.delete(id);
    }

    public GoalDetails addStep(GoalCommands.AddStep command) {
        return change(command.goalId(), goal -> goal.addStep(command.details(), command.status(), clock.instant()));
    }

    public GoalDetails updateStep(GoalCommands.UpdateStep command) {
        return change(command.goalId(), goal -> {
            goal.updateStep(command.stepId(), command.details());
            goal.changeStepStatus(command.stepId(), command.status(), clock.instant());
        });
    }

    /** The click on a timeline mark: Not started → In progress → Done → Not started. */
    public GoalDetails cycleStepStatus(GoalId goalId, StepId stepId) {
        return change(goalId, goal -> goal.cycleStepStatus(stepId, clock.instant()));
    }

    public GoalDetails moveStep(GoalId goalId, StepId stepId, MoveDirection direction) {
        return change(goalId, goal -> goal.moveStep(stepId, direction));
    }

    public GoalDetails removeStep(GoalId goalId, StepId stepId) {
        return change(goalId, goal -> goal.removeStep(stepId));
    }

    /**
     * The steps to work on: not done and planned in the week that contains today (the week starting
     * on {@code weekStart}), plus not-done steps planned before today (overdue, even from earlier
     * weeks). Sorted by planned day, then in roadmap order.
     */
    public List<AgendaItem> agenda(DayOfWeek weekStart) {
        LocalDate today = LocalDate.now(clock);
        LocalDate weekEnd = today.with(TemporalAdjusters.previousOrSame(weekStart)).plusDays(6);
        List<AgendaItem> items = new ArrayList<>();
        for (Goal goal : goals.findAll()) {
            for (RoadmapStep step : goal.steps()) {
                LocalDate planned = step.plannedFor();
                if (planned == null || step.status() == StepStatus.DONE || planned.isAfter(weekEnd)) {
                    continue;
                }
                items.add(new AgendaItem(goal.areaId(), goal.id(), goal.title(), step.id(), step.title(), planned,
                        step.status(), planned.isBefore(today)));
            }
        }
        items.sort(Comparator.comparing(AgendaItem::plannedFor));
        return items;
    }

    private GoalDetails change(GoalId id, Consumer<Goal> change) {
        Goal goal = load(id);
        change.accept(goal);
        return details(goals.save(goal));
    }

    /** The goal plus, for each step, the summary of its learning record (one query for all steps). */
    private GoalDetails details(Goal goal) {
        return GoalDetails.of(goal, records.summariesForSteps(goal.steps().stream().map(RoadmapStep::id).toList()));
    }

    private Goal load(GoalId id) {
        return goals.findById(id).orElseThrow(() -> new NotFoundException("Goal", id));
    }
}
