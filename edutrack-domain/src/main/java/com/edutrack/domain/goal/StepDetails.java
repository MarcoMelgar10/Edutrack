package com.edutrack.domain.goal;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The editable content of a roadmap step, as entered by the user. Not validated here;
 * {@link RoadmapStep} validates and normalizes it when it is applied. Links, notes and
 * results live in the step's learning record, not on the step itself. {@code plannedFor} is the
 * day the learner plans to work on the step; it may be in the past (then the step is overdue).
 */
public record StepDetails(String title, String description, BigDecimal estimatedHours, LocalDate plannedFor) {

    public StepDetails(String title, String description, BigDecimal estimatedHours) {
        this(title, description, estimatedHours, null);
    }
}
