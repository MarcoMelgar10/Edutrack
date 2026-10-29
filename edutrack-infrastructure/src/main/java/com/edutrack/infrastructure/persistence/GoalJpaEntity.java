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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A goal row with its roadmap steps. The area is referenced by id only: areas and goals are
 * separate aggregates, so there is deliberately no association to {@link AreaJpaEntity}.
 */
@Entity
@Table(name = "goal")
public class GoalJpaEntity {

    @Id
    private UUID id;

    @Column(name = "area_id", nullable = false)
    private UUID areaId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "deadline")
    private LocalDate deadline;

    @Column(name = "weekly_target_minutes")
    private Integer weeklyTargetMinutes;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Left {@code null} on new entities: that is how Spring Data knows to persist instead of merge. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "goal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<RoadmapStepJpaEntity> steps = new ArrayList<>();

    protected GoalJpaEntity() {
    }

    public GoalJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAreaId() {
        return areaId;
    }

    public void setAreaId(UUID areaId) {
        this.areaId = areaId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDate deadline) {
        this.deadline = deadline;
    }

    public Integer getWeeklyTargetMinutes() {
        return weeklyTargetMinutes;
    }

    public void setWeeklyTargetMinutes(Integer weeklyTargetMinutes) {
        this.weeklyTargetMinutes = weeklyTargetMinutes;
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

    /** The managed collection: change it in place, never replace it (orphan removal depends on that). */
    public List<RoadmapStepJpaEntity> getSteps() {
        return steps;
    }
}
