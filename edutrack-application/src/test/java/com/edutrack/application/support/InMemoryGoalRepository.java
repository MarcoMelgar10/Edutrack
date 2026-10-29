package com.edutrack.application.support;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryGoalRepository implements GoalRepository {

    private final Map<GoalId, Goal> goals = new LinkedHashMap<>();

    @Override
    public List<Goal> findByArea(AreaId areaId) {
        return goals.values().stream()
                .filter(goal -> goal.areaId().equals(areaId))
                .sorted(Comparator.comparingInt(Goal::position))
                .toList();
    }

    @Override
    public List<Goal> findAll() {
        return goals.values().stream().sorted(Comparator.comparingInt(Goal::position)).toList();
    }

    @Override
    public Optional<Goal> findById(GoalId id) {
        return Optional.ofNullable(goals.get(id));
    }

    @Override
    public Goal save(Goal goal) {
        goals.put(goal.id(), goal);
        return goal;
    }

    @Override
    public void delete(GoalId id) {
        goals.remove(id);
    }
}
