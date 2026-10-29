package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.journal.EntryKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A journal entry row. The area, goal and step are referenced by id only: they are separate
 * aggregates, and the database clears the link when the target is deleted, keeping the entry.
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntryJpaEntity {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private EntryKind kind;

    @Column(name = "written_on", nullable = false)
    private LocalDate writtenOn;

    @Column(name = "title", length = 120)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Column(name = "area_id")
    private UUID areaId;

    @Column(name = "goal_id")
    private UUID goalId;

    @Column(name = "step_id")
    private UUID stepId;

    @JdbcTypeCode(SqlTypes.SMALLINT)
    @Column(name = "confidence")
    private Integer confidence;

    @Column(name = "week_minutes")
    private Integer weekMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Left {@code null} on new entities: that is how Spring Data knows to persist instead of merge. */
    @Version
    private Long version;

    protected JournalEntryJpaEntity() {
    }

    public JournalEntryJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public EntryKind getKind() {
        return kind;
    }

    public void setKind(EntryKind kind) {
        this.kind = kind;
    }

    public LocalDate getWrittenOn() {
        return writtenOn;
    }

    public void setWrittenOn(LocalDate writtenOn) {
        this.writtenOn = writtenOn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public void setAreaId(UUID areaId) {
        this.areaId = areaId;
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

    public Integer getConfidence() {
        return confidence;
    }

    public void setConfidence(Integer confidence) {
        this.confidence = confidence;
    }

    public Integer getWeekMinutes() {
        return weekMinutes;
    }

    public void setWeekMinutes(Integer weekMinutes) {
        this.weekMinutes = weekMinutes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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
}
