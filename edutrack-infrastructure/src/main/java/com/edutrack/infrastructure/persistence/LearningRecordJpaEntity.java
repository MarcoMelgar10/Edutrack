package com.edutrack.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A learning record row with its resources, exercises and results. The owner is referenced by id
 * only (goal or step, exactly one is set): the record is a separate aggregate from the goal.
 */
@Entity
@Table(name = "learning_record")
public class LearningRecordJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "notes", nullable = false, columnDefinition = "text")
    private String notes;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Left {@code null} on new entities: that is how Spring Data knows to persist instead of merge. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LearningResourceJpaEntity> resources = new ArrayList<>();

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LearningExerciseJpaEntity> exercises = new ArrayList<>();

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<LearningResultJpaEntity> results = new ArrayList<>();

    protected LearningRecordJpaEntity() {
    }

    public LearningRecordJpaEntity(UUID id) {
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

    public UUID getStepId() {
        return stepId;
    }

    public void setStepId(UUID stepId) {
        this.stepId = stepId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /** The managed collections: change them in place, never replace them (orphan removal depends on that). */
    public List<LearningResourceJpaEntity> getResources() {
        return resources;
    }

    public List<LearningExerciseJpaEntity> getExercises() {
        return exercises;
    }

    public List<LearningResultJpaEntity> getResults() {
        return results;
    }
}
