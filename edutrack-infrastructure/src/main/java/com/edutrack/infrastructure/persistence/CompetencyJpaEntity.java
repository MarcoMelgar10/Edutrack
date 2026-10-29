package com.edutrack.infrastructure.persistence;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A competency row with its dated level history and the ids of the steps linked to it. The goal
 * and the steps are referenced by id only (separate aggregates), so there are no associations.
 */
@Entity
@Table(name = "competency")
public class CompetencyJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "target_level", nullable = false)
    private short targetLevel;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Left {@code null} on new entities: that is how Spring Data knows to persist instead of merge. */
    @Version
    private Long version;

    /** Level set on each day; one row per day. */
    @ElementCollection
    @CollectionTable(name = "competency_level_change", joinColumns = @JoinColumn(name = "competency_id"))
    @MapKeyColumn(name = "changed_on")
    @Column(name = "level", nullable = false)
    private Map<LocalDate, Short> history = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "step_competency", joinColumns = @JoinColumn(name = "competency_id"))
    @Column(name = "step_id", nullable = false)
    private Set<UUID> stepIds = new HashSet<>();

    protected CompetencyJpaEntity() {
    }

    public CompetencyJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public void setGoalId(UUID goalId) {
        this.goalId = goalId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public short getLevel() {
        return level;
    }

    public void setLevel(short level) {
        this.level = level;
    }

    public short getTargetLevel() {
        return targetLevel;
    }

    public void setTargetLevel(short targetLevel) {
        this.targetLevel = targetLevel;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /** The managed collection: change it in place, never replace it. */
    public Map<LocalDate, Short> getHistory() {
        return history;
    }

    /** The managed collection: change it in place, never replace it. */
    public Set<UUID> getStepIds() {
        return stepIds;
    }
}
