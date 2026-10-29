package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.review.CardSide;
import com.edutrack.domain.review.Grade;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the append-only review history. The card is referenced by id only. It has no version
 * column, so it tells Spring Data it is new itself and is inserted without a lookup first.
 */
@Entity
@Table(name = "review_log")
public class ReviewLogJpaEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "card_id", nullable = false)
    private UUID cardId;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", nullable = false, length = 10)
    private CardSide side;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "grade", nullable = false, length = 10)
    private Grade grade;

    @Column(name = "seconds", nullable = false)
    private int seconds;

    @Column(name = "was_new", nullable = false)
    private boolean wasNew;

    @Transient
    private boolean fresh = true;

    protected ReviewLogJpaEntity() {
    }

    public ReviewLogJpaEntity(UUID id, UUID cardId, CardSide side, Instant reviewedAt, Grade grade, int seconds,
                              boolean wasNew) {
        this.id = id;
        this.cardId = cardId;
        this.side = side;
        this.reviewedAt = reviewedAt;
        this.grade = grade;
        this.seconds = seconds;
        this.wasNew = wasNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    @PostLoad
    @PostPersist
    void markStored() {
        fresh = false;
    }

    public UUID getCardId() {
        return cardId;
    }

    public CardSide getSide() {
        return side;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Grade getGrade() {
        return grade;
    }

    public int getSeconds() {
        return seconds;
    }

    public boolean isWasNew() {
        return wasNew;
    }
}
