package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.review.CardId;
import com.edutrack.domain.review.ReviewLog;
import com.edutrack.domain.review.ReviewLogId;
import com.edutrack.domain.review.ReviewLogRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class JpaReviewLogRepositoryAdapter implements ReviewLogRepository {

    private final SpringDataReviewLogRepository repository;

    JpaReviewLogRepositoryAdapter(SpringDataReviewLogRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void append(ReviewLog log) {
        repository.saveAndFlush(new ReviewLogJpaEntity(log.id().value(), log.cardId().value(), log.side(),
                log.reviewedAt(), log.grade(), log.seconds(), log.wasNew()));
    }

    /** The local day is turned into an instant range in {@code zone}, which stays right across daylight-saving changes. */
    @Override
    @Transactional(readOnly = true)
    public int countOn(LocalDate day, ZoneId zone, boolean newOnly) {
        Instant from = day.atStartOfDay(zone).toInstant();
        Instant to = day.plusDays(1).atStartOfDay(zone).toInstant();
        return (int) (newOnly
                ? repository.countByReviewedAtGreaterThanEqualAndReviewedAtLessThanAndWasNewTrue(from, to)
                : repository.countByReviewedAtGreaterThanEqualAndReviewedAtLessThan(from, to));
    }

    @Override
    @Transactional(readOnly = true)
    public Set<LocalDate> reviewDays(LocalDate from, LocalDate to, ZoneId zone) {
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return repository.reviewTimes(start, end).stream()
                .map(time -> time.atZone(zone).toLocalDate())
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewLog> findBetween(Instant from, Instant to) {
        return repository.findByReviewedAtGreaterThanEqualAndReviewedAtLessThanOrderByReviewedAtAscIdAsc(from, to)
                .stream()
                .map(entity -> new ReviewLog(new ReviewLogId(entity.getId()), new CardId(entity.getCardId()),
                        entity.getSide(), entity.getReviewedAt(), entity.getGrade(), entity.getSeconds(),
                        entity.isWasNew()))
                .toList();
    }
}
