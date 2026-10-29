package com.edutrack.application.progress;

import java.time.LocalDate;
import java.util.Set;

/**
 * Something that makes a day count as a study day for the streak: a logged session, a card
 * review, a weekly check-in. Each feature contributes one source; the consistency service
 * merges them.
 */
@FunctionalInterface
public interface StudyDaySource {

    /** The days between {@code from} and {@code to} (both included) with at least one activity. */
    Set<LocalDate> studyDays(LocalDate from, LocalDate to);
}
