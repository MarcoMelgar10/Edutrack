package com.edutrack.application.support;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.practice.PracticeRoutine;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.practice.PracticeRoutineRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryPracticeRoutineRepository implements PracticeRoutineRepository {

    private final Map<PracticeRoutineId, PracticeRoutine> routines = new LinkedHashMap<>();

    @Override
    public PracticeRoutine save(PracticeRoutine routine) {
        routines.put(routine.id(), routine);
        return routine;
    }

    @Override
    public Optional<PracticeRoutine> findById(PracticeRoutineId id) {
        return Optional.ofNullable(routines.get(id));
    }

    @Override
    public void delete(PracticeRoutineId id) {
        routines.remove(id);
    }

    @Override
    public List<PracticeRoutine> findByGoal(GoalId goalId) {
        return routines.values().stream()
                .filter(routine -> routine.goalId().equals(goalId))
                .sorted(Comparator.comparingInt(PracticeRoutine::position))
                .toList();
    }

    @Override
    public List<PracticeRoutine> findActive() {
        return routines.values().stream()
                .filter(PracticeRoutine::active)
                .sorted(Comparator.comparingInt(PracticeRoutine::position))
                .toList();
    }
}
