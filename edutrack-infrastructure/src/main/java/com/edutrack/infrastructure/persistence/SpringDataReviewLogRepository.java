package com.edutrack.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface SpringDataReviewLogRepository extends JpaRepository<ReviewLogJpaEntity, UUID> {

    long countByReviewedAtGreaterThanEqualAndReviewedAtLessThan(Instant from, Instant to);

    long countByReviewedAtGreaterThanEqualAndReviewedAtLessThanAndWasNewTrue(Instant from, Instant to);

    List<ReviewLogJpaEntity> findByReviewedAtGreaterThanEqualAndReviewedAtLessThanOrderByReviewedAtAscIdAsc(
            Instant from, Instant to);

    @Query("select r.reviewedAt from ReviewLogJpaEntity r where r.reviewedAt >= :from and r.reviewedAt < :to")
    List<Instant> reviewTimes(@Param("from") Instant from, @Param("to") Instant to);
}
