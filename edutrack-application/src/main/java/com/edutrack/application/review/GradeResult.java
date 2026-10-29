package com.edutrack.application.review;

import java.time.LocalDate;

/** What grading a card side led to, for the "Next review in 6 days" hint. A lapse is due today with a 0-day interval. */
public record GradeResult(LocalDate nextDue, int intervalDays) {
}
