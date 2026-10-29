package com.edutrack.application.insights;

import com.edutrack.domain.progress.Progress;

import java.time.LocalDate;

/** A goal's progress on a day, for the progress-over-time chart. */
public record ProgressPoint(LocalDate on, Progress progress) {
}
