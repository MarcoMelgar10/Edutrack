package com.edutrack.application.support;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.CompetencyRepository;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryCompetencyRepository implements CompetencyRepository {

    private final Map<CompetencyId, Competency> competencies = new LinkedHashMap<>();

    @Override
    public Competency save(Competency competency) {
        competencies.put(competency.id(), competency);
        return competency;
    }

    @Override
    public Optional<Competency> findById(CompetencyId id) {
        return Optional.ofNullable(competencies.get(id));
    }

    @Override
    public void delete(CompetencyId id) {
        competencies.remove(id);
    }

    @Override
    public List<Competency> findByGoal(GoalId goalId) {
        return competencies.values().stream()
                .filter(competency -> competency.goalId().equals(goalId))
                .sorted(Comparator.comparingInt(Competency::position))
                .toList();
    }

    @Override
    public List<Competency> findByStep(StepId stepId) {
        return competencies.values().stream()
                .filter(competency -> competency.steps().contains(stepId))
                .sorted(Comparator.comparingInt(Competency::position))
                .toList();
    }
}
