package com.edutrack.application.support;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryMeasureRepository implements MeasureRepository {

    private final Map<MeasureId, Measure> measures = new LinkedHashMap<>();

    @Override
    public Measure save(Measure measure) {
        measures.put(measure.id(), measure);
        return measure;
    }

    @Override
    public Optional<Measure> findById(MeasureId id) {
        return Optional.ofNullable(measures.get(id));
    }

    @Override
    public void delete(MeasureId id) {
        measures.remove(id);
    }

    @Override
    public List<Measure> findByGoal(GoalId goalId) {
        return measures.values().stream()
                .filter(measure -> measure.goalId().equals(goalId))
                .sorted(Comparator.comparingInt(Measure::position))
                .toList();
    }
}
