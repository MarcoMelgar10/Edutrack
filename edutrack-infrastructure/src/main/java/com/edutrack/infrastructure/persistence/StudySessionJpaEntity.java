package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.session.SessionSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * A study session row. The goal, step and routine are referenced by id only: they are separate
 * aggregates, and the database clears the step and routine links when those are deleted.
 */
@Entity
@Table(name = "study_session")
public class StudySessionJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "routine_id")
    private UUID routineId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "minutes", nullable = false)
    private int minutes;

    @Column(name = "note", length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private SessionSource source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Left {@code null} on new entities: that is how Spring Data knows to persist instead of merge. */
    @Version
    private Long version;

    protected StudySessionJpaEntity() {
    }

    public StudySessionJpaEntity(UUID id) {
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

    public UUID getRoutineId() {
        return routineId;
    }

    public void setRoutineId(UUID routineId) {
        this.routineId = routineId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public int getMinutes() {
        return minutes;
    }

    public void setMinutes(int minutes) {
        this.minutes = minutes;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public SessionSource getSource() {
        return source;
    }

    public void setSource(SessionSource source) {
        this.source = source;
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
}
