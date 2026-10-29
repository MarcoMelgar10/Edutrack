package com.edutrack.application.skill;

import com.edutrack.application.NotFoundException;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.CompetencyRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Use cases for a goal's competencies: what it builds, how far the learner has got and which
 * roadmap steps contribute. Every change returns the refreshed {@link CompetencyView}.
 */
public class CompetencyService {

    private final CompetencyRepository competencies;
    private final GoalRepository goals;
    private final Clock clock;

    public CompetencyService(CompetencyRepository competencies, GoalRepository goals, Clock clock) {
        this.competencies = competencies;
        this.goals = goals;
        this.clock = clock;
    }

    /** The goal's competencies in display order. */
    public List<CompetencyView> forGoal(GoalId goalId) {
        Goal goal = goal(goalId);
        return competencies.findByGoal(goalId).stream().map(competency -> CompetencyView.of(competency, goal)).toList();
    }

    /** The competencies a step contributes to (empty when it is linked to none). */
    public List<CompetencyView> forStep(StepId stepId) {
        Map<GoalId, Goal> loaded = new HashMap<>();
        List<CompetencyView> views = new ArrayList<>();
        for (Competency competency : competencies.findByStep(stepId)) {
            Goal goal = loaded.computeIfAbsent(competency.goalId(), this::goal);
            views.add(CompetencyView.of(competency, goal));
        }
        return views;
    }

    public CompetencyView create(GoalId goalId, CompetencyDetails details) {
        Goal goal = goal(goalId);
        int position = competencies.findByGoal(goalId).stream().mapToInt(Competency::position).max().orElse(-1) + 1;
        Competency competency = Competency.create(goalId, details, position, clock.instant());
        return CompetencyView.of(competencies.save(competency), goal);
    }

    public CompetencyView update(CompetencyId id, CompetencyDetails details) {
        return change(id, competency -> competency.update(details));
    }

    /** Sets the current level (0–5) and records it in the history under today's date. */
    public CompetencyView changeLevel(CompetencyId id, int level) {
        return change(id, competency -> competency.changeLevel(level, LocalDate.now(clock)));
    }

    /** Links these steps in addition to the ones already linked; every step must belong to the competency's goal. */
    public CompetencyView linkSteps(CompetencyId id, Set<StepId> stepIds) {
        return changeSteps(id, stepIds, Competency::linkSteps);
    }

    /** Makes exactly these steps the linked ones (for the edit dialog's checklist). */
    public CompetencyView setSteps(CompetencyId id, Set<StepId> stepIds) {
        return changeSteps(id, stepIds, Competency::setSteps);
    }

    public CompetencyView unlinkStep(CompetencyId id, StepId stepId) {
        return change(id, competency -> competency.unlinkStep(stepId));
    }

    /** Swaps the competency with its neighbour in its goal's list; moving past either end does nothing. */
    public List<CompetencyView> move(CompetencyId id, MoveDirection direction) {
        Competency moved = load(id);
        List<Competency> ordered = new ArrayList<>(competencies.findByGoal(moved.goalId()));
        int from = ordered.indexOf(moved);
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (from >= 0 && to >= 0 && to < ordered.size()) {
            Collections.swap(ordered, from, to);
            for (int position = 0; position < ordered.size(); position++) {
                Competency competency = ordered.get(position);
                if (competency.position() != position) {
                    competency.moveTo(position);
                    competencies.save(competency);
                }
            }
        }
        return forGoal(moved.goalId());
    }

    /** Deleting a competency keeps the goal's measures; they just stop pointing at it. */
    public void delete(CompetencyId id) {
        competencies.delete(id);
    }

    private CompetencyView changeSteps(CompetencyId id, Set<StepId> stepIds,
                                       BiConsumer<Competency, Set<StepId>> action) {
        Competency competency = load(id);
        Goal goal = goal(competency.goalId());
        Set<StepId> own = new HashSet<>();
        goal.steps().stream().map(RoadmapStep::id).forEach(own::add);
        for (StepId stepId : stepIds) {
            if (!own.contains(stepId)) {
                throw new NotFoundException("Step", stepId);
            }
        }
        action.accept(competency, Set.copyOf(stepIds));
        return CompetencyView.of(competencies.save(competency), goal);
    }

    private CompetencyView change(CompetencyId id, Consumer<Competency> change) {
        Competency competency = load(id);
        change.accept(competency);
        return CompetencyView.of(competencies.save(competency), goal(competency.goalId()));
    }

    private Competency load(CompetencyId id) {
        return competencies.findById(id).orElseThrow(() -> new NotFoundException("Competency", id));
    }

    private Goal goal(GoalId id) {
        return goals.findById(id).orElseThrow(() -> new NotFoundException("Goal", id));
    }
}
