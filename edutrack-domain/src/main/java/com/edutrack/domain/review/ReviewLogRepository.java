package com.edutrack.domain.review;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** Port for the append-only review history. Implemented by the infrastructure layer. */
public interface ReviewLogRepository {

    void append(ReviewLog log);

    /**
     * Reviews done on the local calendar {@code day} in {@code zone}. With {@code newOnly} only the
     * first-time reviews of a side are counted (the new cards introduced that day); otherwise all of them.
     */
    int countOn(LocalDate day, ZoneId zone, boolean newOnly);

    /** The local days between {@code from} and {@code to} (both included) with at least one review. */
    Set<LocalDate> reviewDays(LocalDate from, LocalDate to, ZoneId zone);

    /** Reviews from {@code from} (included) until {@code to} (excluded), oldest first. */
    List<ReviewLog> findBetween(Instant from, Instant to);
}
