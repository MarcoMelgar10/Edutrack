package com.edutrack.application.review;

import com.edutrack.application.progress.StudyDaySource;
import com.edutrack.domain.review.ReviewLogRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

/** Card reviews as a source of study days: any day with at least one answered card counts. */
public class ReviewStudyDays implements StudyDaySource {

    private final ReviewLogRepository logs;
    private final Clock clock;

    public ReviewStudyDays(ReviewLogRepository logs, Clock clock) {
        this.logs = logs;
        this.clock = clock;
    }

    @Override
    public Set<LocalDate> studyDays(LocalDate from, LocalDate to) {
        return logs.reviewDays(from, to, clock.getZone());
    }
}
