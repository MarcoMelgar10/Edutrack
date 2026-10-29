package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.review.CardSide;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * A card row with the schedule of each side it has. The goal and the step are referenced by id
 * only (separate aggregates). Tags are a PostgreSQL {@code text[]}.
 */
@Entity
@Table(name = "card")
public class CardJpaEntity {

    @Id
    private UUID id;

    @Column(name = "goal_id", nullable = false)
    private UUID goalId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "front", nullable = false, columnDefinition = "text")
    private String front;

    @Column(name = "back", nullable = false, columnDefinition = "text")
    private String back;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", nullable = false, columnDefinition = "text[]")
    private String[] tags = new String[0];

    @Column(name = "reversible", nullable = false)
    private boolean reversible;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Left {@code null} on new entities: that is how Spring Data knows to persist instead of merge. */
    @Version
    private Long version;

    @ElementCollection
    @CollectionTable(name = "card_schedule", joinColumns = @JoinColumn(name = "card_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "side", length = 10)
    private Map<CardSide, CardScheduleEmbeddable> schedules = new EnumMap<>(CardSide.class);

    protected CardJpaEntity() {
    }

    public CardJpaEntity(UUID id) {
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

    public String getFront() {
        return front;
    }

    public void setFront(String front) {
        this.front = front;
    }

    public String getBack() {
        return back;
    }

    public void setBack(String back) {
        this.back = back;
    }

    public String[] getTags() {
        return tags;
    }

    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public boolean isReversible() {
        return reversible;
    }

    public void setReversible(boolean reversible) {
        this.reversible = reversible;
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
    public Map<CardSide, CardScheduleEmbeddable> getSchedules() {
        return schedules;
    }
}
