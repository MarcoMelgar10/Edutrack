package com.edutrack.application.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Input for the goal and roadmap use cases, as typed in the Goal and Step dialogs. Validated by the domain. */
public final class GoalCommands {

    private GoalCommands() {
    }

    /** {@code weeklyTargetMinutes} is the optional study time per week the learner wants to give the goal. */
    public record CreateGoal(AreaId areaId, String title, String description, LocalDate deadline,
                             Integer weeklyTargetMinutes) {

        public CreateGoal(AreaId areaId, String title, String description, LocalDate deadline) {
            this(areaId, title, description, deadline, null);
        }
    }

    /** A missing {@code weeklyTargetMinutes} removes the goal's weekly target. */
    public record UpdateGoal(GoalId goalId, String title, String description, LocalDate deadline,
                             Integer weeklyTargetMinutes) {

        public UpdateGoal(GoalId goalId, String title, String description, LocalDate deadline) {
            this(goalId, title, description, deadline, null);
        }
    }

    /** A new step goes to the end of the roadmap; a missing status means Not started. */
    public record AddStep(GoalId goalId, String title, String description, BigDecimal estimatedHours,
                          StepStatus status, LocalDate plannedFor) {

        public AddStep {
            status = status == null ? StepStatus.NOT_STARTED : status;
        }

        public AddStep(GoalId goalId, String title, String description, BigDecimal estimatedHours,
                       StepStatus status) {
            this(goalId, title, description, estimatedHours, status, null);
        }

        public StepDetails details() {
            return new StepDetails(title, description, estimatedHours, plannedFor);
        }
    }

    /** A missing {@code plannedFor} clears the step's planned day. */
    public record UpdateStep(GoalId goalId, StepId stepId, String title, String description,
                             BigDecimal estimatedHours, StepStatus status, LocalDate plannedFor) {

        public UpdateStep {
            status = status == null ? StepStatus.NOT_STARTED : status;
        }

        public UpdateStep(GoalId goalId, StepId stepId, String title, String description,
                          BigDecimal estimatedHours, StepStatus status) {
            this(goalId, stepId, title, description, estimatedHours, status, null);
        }

        public StepDetails details() {
            return new StepDetails(title, description, estimatedHours, plannedFor);
        }
    }
}
