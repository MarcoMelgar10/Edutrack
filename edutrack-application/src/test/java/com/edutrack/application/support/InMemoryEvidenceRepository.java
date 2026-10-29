package com.edutrack.application.support;

import com.edutrack.domain.evidence.Evidence;
import com.edutrack.domain.evidence.EvidenceId;
import com.edutrack.domain.evidence.EvidenceRepository;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class InMemoryEvidenceRepository implements EvidenceRepository {

    private static final Comparator<Evidence> NEWEST_FIRST =
            Comparator.comparing(Evidence::takenOn).thenComparing(Evidence::createdAt).reversed();

    private final Map<EvidenceId, Evidence> items = new LinkedHashMap<>();
    private boolean failOnSave;

    @Override
    public Evidence save(Evidence evidence) {
        if (failOnSave) {
            throw new IllegalStateException("database is down");
        }
        items.put(evidence.id(), evidence);
        return evidence;
    }

    @Override
    public Optional<Evidence> findById(EvidenceId id) {
        return Optional.ofNullable(items.get(id));
    }

    @Override
    public void delete(EvidenceId id) {
        items.remove(id);
    }

    @Override
    public List<Evidence> findByGoal(GoalId goalId) {
        return items.values().stream().filter(item -> item.goalId().equals(goalId)).sorted(NEWEST_FIRST).toList();
    }

    @Override
    public List<Evidence> findByStep(StepId stepId) {
        return items.values().stream().filter(item -> Objects.equals(item.stepId(), stepId)).sorted(NEWEST_FIRST).toList();
    }

    public void failOnSave(boolean fail) {
        this.failOnSave = fail;
    }

    public int size() {
        return items.size();
    }
}
