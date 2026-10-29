package com.edutrack.application.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;

import java.time.LocalDate;

/** A step with a planned day that is not done yet, as the week agenda lists it. */
public record AgendaItem(AreaId areaId, GoalId goalId, String goalTitle, StepId stepId, String stepTitle,
                         LocalDate plannedFor, StepStatus status, boolean overdue) {
}
