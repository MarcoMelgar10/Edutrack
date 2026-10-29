package com.edutrack.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "learning_exercise")
public class LearningExerciseJpaEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private LearningRecordJpaEntity record;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "done", nullable = false)
    private boolean done;

    @Column(name = "done_at")
    private Instant doneAt;

    protected LearningExerciseJpaEntity() {
    }

    public LearningExerciseJpaEntity(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public LearningRecordJpaEntity getRecord() {
        return record;
    }

    public void setRecord(LearningRecordJpaEntity record) {
        this.record = record;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    public Instant getDoneAt() {
        return doneAt;
    }

    public void setDoneAt(Instant doneAt) {
        this.doneAt = doneAt;
    }
}
