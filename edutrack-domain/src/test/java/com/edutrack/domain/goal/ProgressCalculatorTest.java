package com.edutrack.domain.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.progress.Progress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProgressCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final Goal goal = Goal.create(AreaId.newId(), "Goal", null, null, 0, NOW);

    private void addStep(String hours, StepStatus status) {
        BigDecimal estimate = hours == null ? null : new BigDecimal(hours);
        goal.addStep(new StepDetails("Step", null, estimate), status, NOW);
    }

    @Test
    void progressIsDoneHoursOverTotalHours() {
        addStep("10", StepStatus.DONE);
        addStep("20", StepStatus.IN_PROGRESS);
        addStep("8", StepStatus.NOT_STARTED);

        Progress progress = ProgressCalculator.ofSteps(goal.steps());

        assertThat(progress.percent()).isEqualByComparingTo("26.32");
        assertThat(progress.rounded()).isEqualTo(26);
    }

    @Test
    void effortReportsDoneAndTotalHours() {
        addStep("10", StepStatus.DONE);
        addStep("20", StepStatus.IN_PROGRESS);
        addStep("8", StepStatus.NOT_STARTED);

        ProgressCalculator.Effort effort = ProgressCalculator.effortOf(goal.steps());

        assertThat(effort.doneHours()).isEqualByComparingTo("10.0");
        assertThat(effort.totalHours()).isEqualByComparingTo("38.0");
    }

    @Test
    void stepsWithoutHoursOrWithZeroHoursWeighOneHour() {
        addStep(null, StepStatus.DONE);
        addStep("0", StepStatus.NOT_STARTED);
        addStep("2", StepStatus.NOT_STARTED);

        assertThat(ProgressCalculator.ofSteps(goal.steps()).percent()).isEqualByComparingTo("25");
        assertThat(ProgressCalculator.effortOf(goal.steps()).totalHours()).isEqualByComparingTo("4");
    }

    @Test
    void weightOfFallsBackToOneHourOnlyWithoutAnEstimate() {
        addStep(null, StepStatus.NOT_STARTED);
        addStep("0", StepStatus.NOT_STARTED);
        addStep("2.5", StepStatus.NOT_STARTED);

        List<BigDecimal> weights = goal.steps().stream().map(ProgressCalculator::weightOf).toList();

        assertThat(weights).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("2.5"));
    }

    @Test
    void inProgressStepsCountAsZero() {
        addStep("5", StepStatus.IN_PROGRESS);

        assertThat(ProgressCalculator.ofSteps(goal.steps())).isEqualTo(Progress.ZERO);
    }

    @Test
    void aGoalWithoutStepsIsAtZero() {
        assertThat(ProgressCalculator.ofSteps(goal.steps())).isEqualTo(Progress.ZERO);
    }

    @Test
    void aGoalWithAllStepsDoneIsComplete() {
        addStep("3", StepStatus.DONE);
        addStep(null, StepStatus.DONE);

        assertThat(ProgressCalculator.ofSteps(goal.steps())).isEqualTo(Progress.COMPLETE);
    }

    @Test
    void effortSeparatesEnteredHoursFromStepsWithoutEstimate() {
        addStep("2.5", StepStatus.DONE);
        addStep("30", StepStatus.DONE);
        addStep("162", StepStatus.NOT_STARTED);
        addStep(null, StepStatus.NOT_STARTED);

        ProgressCalculator.Effort effort = ProgressCalculator.effortOf(goal.steps());

        assertThat(effort.totalHours()).isEqualByComparingTo("195.5");
        assertThat(effort.estimatedHours()).isEqualByComparingTo("194.5");
        assertThat(effort.estimatedDoneHours()).isEqualByComparingTo("32.5");
        assertThat(effort.unestimatedSteps()).isEqualTo(1);
    }

    @Test
    void aGoalWithoutAnyEstimateHasNoEnteredHours() {
        addStep(null, StepStatus.DONE);
        addStep(null, StepStatus.NOT_STARTED);
        addStep("0", StepStatus.NOT_STARTED);

        ProgressCalculator.Effort effort = ProgressCalculator.effortOf(goal.steps());

        assertThat(effort.estimatedHours()).isEqualByComparingTo("0");
        assertThat(effort.estimatedDoneHours()).isEqualByComparingTo("0");
        assertThat(effort.unestimatedSteps()).isEqualTo(3);
        assertThat(effort.progress().rounded()).isEqualTo(33);
    }

    @Test
    void areaProgressWeighsGoalsByTheirHours() {
        // IELTS: 33 of 196 h done (17%); a short course: 8 of 8 h done (100%).
        ProgressCalculator.Effort ielts = effort("33", "196");
        ProgressCalculator.Effort course = effort("8", "8");

        Progress area = ProgressCalculator.ofGoals(List.of(ielts, course));

        assertThat(area.percent()).isEqualByComparingTo("20.10");  // 41 / 204 h
    }

    @Test
    void goalsWithoutStepsDoNotPullTheAreaDown() {
        ProgressCalculator.Effort ielts = effort("33", "196");
        ProgressCalculator.Effort empty = ProgressCalculator.effortOf(List.of());

        assertThat(ProgressCalculator.ofGoals(List.of(ielts, empty)))
                .isEqualTo(ProgressCalculator.ofGoals(List.of(ielts)));
    }

    @Test
    void anAreaWithoutHoursIsAtZero() {
        assertThat(ProgressCalculator.ofGoals(List.of())).isEqualTo(Progress.ZERO);
        assertThat(ProgressCalculator.ofGoals(List.of(ProgressCalculator.effortOf(List.of()))))
                .isEqualTo(Progress.ZERO);
    }

    private static ProgressCalculator.Effort effort(String done, String total) {
        return new ProgressCalculator.Effort(new BigDecimal(done), new BigDecimal(total),
                new BigDecimal(done), new BigDecimal(total), 0);
    }
}
