package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.LevelChange;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

final class CompetencyMapper {

    private CompetencyMapper() {
    }

    /** Must run inside the transaction that loaded the entity, so the lazy collections can be read. */
    static Competency toDomain(CompetencyJpaEntity entity) {
        List<LevelChange> history = entity.getHistory().entrySet().stream()
                .map(change -> new LevelChange(change.getKey(), change.getValue()))
                .sorted(Comparator.comparing(LevelChange::on))
                .toList();
        Set<StepId> steps = entity.getStepIds().stream().map(StepId::new).collect(Collectors.toSet());
        return Competency.restore(
                new CompetencyId(entity.getId()),
                new GoalId(entity.getGoalId()),
                new CompetencyDetails(entity.getName(), entity.getDescription(), (int) entity.getTargetLevel()),
                entity.getLevel(),
                entity.getPosition(),
                entity.getCreatedAt(),
                history,
                steps);
    }

    /** Brings the entity in line with the competency; the managed collections are changed in place, never replaced. */
    static void copyToEntity(Competency competency, CompetencyJpaEntity entity) {
        entity.setGoalId(competency.goalId().value());
        entity.setName(competency.name());
        entity.setDescription(competency.description());
        entity.setLevel((short) competency.level());
        entity.setTargetLevel((short) competency.targetLevel());
        entity.setPosition(competency.position());
        entity.setCreatedAt(competency.createdAt());

        Map<LocalDate, Short> history = new HashMap<>();
        competency.history().forEach(change -> history.put(change.on(), (short) change.level()));
        entity.getHistory().keySet().retainAll(history.keySet());
        entity.getHistory().putAll(history);

        Set<UUID> steps = competency.steps().stream().map(StepId::value).collect(Collectors.toSet());
        entity.getStepIds().retainAll(steps);
        entity.getStepIds().addAll(steps);
    }
}
