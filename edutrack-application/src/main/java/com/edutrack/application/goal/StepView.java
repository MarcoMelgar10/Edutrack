package com.edutrack.application.goal;

import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.RecordSummary;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One row of the roadmap timeline; {@code number} starts at 1. {@code record} summarizes the
 * step's learning record ("Notes · 3 resources · Exercises 2/5"). {@code plannedFor} is the day the
 * learner plans to work on the step, or {@code null}.
 */
public record StepView(StepId id, int number, String title, String description, BigDecimal estimatedHours,
                       StepStatus status, Instant completedAt, RecordSummary record, LocalDate plannedFor) {

    public StepView(StepId id, int number, String title, String description, BigDecimal estimatedHours,
                    StepStatus status, Instant completedAt, RecordSummary record) {
        this(id, number, title, description, estimatedHours, status, completedAt, record, null);
    }

    static StepView of(RoadmapStep step, int number, RecordSummary record) {
        return new StepView(step.id(), number, step.title(), step.description(), step.estimatedHours(),
                step.status(), step.completedAt(), record, step.plannedFor());
    }
}
