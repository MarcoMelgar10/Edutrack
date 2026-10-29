package com.edutrack.application.support;

import com.edutrack.domain.review.ReviewLog;
import com.edutrack.domain.review.ReviewLogRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class InMemoryReviewLogRepository implements ReviewLogRepository {

    private final List<ReviewLog> logs = new ArrayList<>();

    @Override
    public void append(ReviewLog log) {
        logs.add(log);
    }

    @Override
    public int countOn(LocalDate day, ZoneId zone, boolean newOnly) {
        return (int) logs.stream()
                .filter(log -> day.equals(log.reviewedAt().atZone(zone).toLocalDate()))
                .filter(log -> !newOnly || log.wasNew())
                .count();
    }

    @Override
    public Set<LocalDate> reviewDays(LocalDate from, LocalDate to, ZoneId zone) {
        return logs.stream()
                .map(log -> log.reviewedAt().atZone(zone).toLocalDate())
                .filter(day -> !day.isBefore(from) && !day.isAfter(to))
                .collect(Collectors.toSet());
    }

    @Override
    public List<ReviewLog> findBetween(Instant from, Instant to) {
        return logs.stream()
                .filter(log -> !log.reviewedAt().isBefore(from) && log.reviewedAt().isBefore(to))
                .sorted(Comparator.comparing(ReviewLog::reviewedAt))
                .toList();
    }

    public List<ReviewLog> all() {
        return List.copyOf(logs);
    }
}
