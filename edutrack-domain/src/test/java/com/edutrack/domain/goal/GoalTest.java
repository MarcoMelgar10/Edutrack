package com.edutrack.domain.goal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private final AreaId areaId = AreaId.newId();
    private final Goal goal = Goal.create(areaId, "Pass the B2 exam", null, null, 0, NOW);

    private RoadmapStep addStep(String title, StepStatus status) {
        return goal.addStep(new StepDetails(title, null, null), status, NOW);
    }

    private RoadmapStep addStep(String title) {
        return addStep(title, StepStatus.NOT_STARTED);
    }

    private List<String> titles() {
        return goal.steps().stream().map(RoadmapStep::title).toList();
    }

    private static Goal create(String title, LocalDate deadline, int position) {
        return Goal.create(AreaId.newId(), title, null, deadline, position, NOW);
    }

    private static RoadmapStep restoredStep(String title, StepStatus status, Instant completedAt) {
        return RoadmapStep.restore(StepId.newId(), title, null, null, status, completedAt);
    }

    @Test
    void titleIsRequired() {
        assertThatThrownBy(() -> create("  ", null, 0))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Title is required.");
    }

    @Test
    void titleIsStripped() {
        assertThat(create("  Learn Java  ", null, 0).title()).isEqualTo("Learn Java");
    }

    @Test
    void titleOf121CharactersIsRejected() {
        assertThatThrownBy(() -> create("a".repeat(121), null, 0))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Title must be at most 120 characters.");
    }

    @Test
    void deadlineInThePastIsAllowed() {
        LocalDate past = LocalDate.of(2020, 1, 1);

        assertThat(create("Late goal", past, 0).deadline()).isEqualTo(past);
    }

    @Test
    void deadlineIsOptional() {
        assertThat(create("Open goal", null, 0).deadline()).isNull();
    }

    @Test
    void negativePositionIsRejected() {
        assertThatThrownBy(() -> create("Goal", null, -1))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Position cannot be negative.");
    }

    @Test
    void newGoalBelongsToItsAreaAndHasNoSteps() {
        assertThat(goal.areaId()).isEqualTo(areaId);
        assertThat(goal.steps()).isEmpty();
    }

    @Test
    void addStepAppendsAtTheEndWithTheGivenStatus() {
        addStep("First");
        RoadmapStep second = addStep("Second", StepStatus.IN_PROGRESS);

        assertThat(titles()).containsExactly("First", "Second");
        assertThat(second.status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(second.completedAt()).isNull();
    }

    @Test
    void addingAStepAsDoneSetsCompletedAt() {
        assertThat(addStep("Done already", StepStatus.DONE).completedAt()).isEqualTo(NOW);
    }

    @Test
    void changingToDoneRecordsThatInstant() {
        RoadmapStep step = addStep("Step");

        goal.changeStepStatus(step.id(), StepStatus.DONE, LATER);

        assertThat(step.status()).isEqualTo(StepStatus.DONE);
        assertThat(step.completedAt()).isEqualTo(LATER);
    }

    @Test
    void leavingDoneClearsCompletedAt() {
        RoadmapStep step = addStep("Step", StepStatus.DONE);

        goal.changeStepStatus(step.id(), StepStatus.IN_PROGRESS, LATER);

        assertThat(step.status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(step.completedAt()).isNull();
    }

    @Test
    void settingTheSameStatusAgainKeepsTheOriginalCompletedAt() {
        RoadmapStep step = addStep("Step", StepStatus.DONE);

        goal.changeStepStatus(step.id(), StepStatus.DONE, LATER);

        assertThat(step.completedAt()).isEqualTo(NOW);
    }

    @Test
    void cycleGoesNotStartedInProgressDoneAndBack() {
        RoadmapStep step = addStep("Step");

        assertThat(goal.cycleStepStatus(step.id(), NOW)).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(goal.cycleStepStatus(step.id(), NOW)).isEqualTo(StepStatus.DONE);
        assertThat(goal.cycleStepStatus(step.id(), NOW)).isEqualTo(StepStatus.NOT_STARTED);
        assertThat(step.status()).isEqualTo(StepStatus.NOT_STARTED);
    }

    @Test
    void cycleIntoDoneRecordsTheInstant() {
        RoadmapStep step = addStep("Step", StepStatus.IN_PROGRESS);

        goal.cycleStepStatus(step.id(), LATER);

        assertThat(step.completedAt()).isEqualTo(LATER);
    }

    @Test
    void updateStepReplacesTheDetailsButKeepsTheStatus() {
        RoadmapStep step = addStep("Old", StepStatus.IN_PROGRESS);

        goal.updateStep(step.id(), new StepDetails("New", "Notes", null));

        assertThat(step.title()).isEqualTo("New");
        assertThat(step.description()).isEqualTo("Notes");
        assertThat(step.status()).isEqualTo(StepStatus.IN_PROGRESS);
    }

    @Test
    void movingAStepUpSwapsItWithThePreviousOne() {
        addStep("A");
        addStep("B");
        RoadmapStep c = addStep("C");

        goal.moveStep(c.id(), MoveDirection.UP);

        assertThat(titles()).containsExactly("A", "C", "B");
    }

    @Test
    void movingAStepDownSwapsItWithTheNextOne() {
        RoadmapStep a = addStep("A");
        addStep("B");
        addStep("C");

        goal.moveStep(a.id(), MoveDirection.DOWN);

        assertThat(titles()).containsExactly("B", "A", "C");
    }

    @Test
    void movingTheFirstStepUpDoesNothing() {
        RoadmapStep a = addStep("A");
        addStep("B");

        goal.moveStep(a.id(), MoveDirection.UP);

        assertThat(titles()).containsExactly("A", "B");
    }

    @Test
    void movingTheLastStepDownDoesNothing() {
        addStep("A");
        RoadmapStep b = addStep("B");

        goal.moveStep(b.id(), MoveDirection.DOWN);

        assertThat(titles()).containsExactly("A", "B");
    }

    @Test
    void removeStepTakesItOutOfTheRoadmap() {
        addStep("A");
        RoadmapStep b = addStep("B");
        addStep("C");

        goal.removeStep(b.id());

        assertThat(titles()).containsExactly("A", "C");
    }

    @Test
    void updatingAnUnknownStepIsRejected() {
        assertThatThrownBy(() -> goal.updateStep(StepId.newId(), new StepDetails("X", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void changingTheStatusOfAnUnknownStepIsRejected() {
        assertThatThrownBy(() -> goal.changeStepStatus(StepId.newId(), StepStatus.DONE, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cyclingAnUnknownStepIsRejected() {
        assertThatThrownBy(() -> goal.cycleStepStatus(StepId.newId(), NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void movingAnUnknownStepIsRejected() {
        assertThatThrownBy(() -> goal.moveStep(StepId.newId(), MoveDirection.UP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingAnUnknownStepIsRejected() {
        assertThatThrownBy(() -> goal.removeStep(StepId.newId()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stepsListIsUnmodifiable() {
        RoadmapStep outsider = restoredStep("X", StepStatus.NOT_STARTED, null);

        assertThatThrownBy(() -> goal.steps().add(outsider)).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aGoalWithoutStepsIsNotCompleted() {
        assertThat(goal.isCompleted()).isFalse();
    }

    @Test
    void aGoalWithAStepNotDoneIsNotCompleted() {
        addStep("A", StepStatus.DONE);
        addStep("B", StepStatus.IN_PROGRESS);

        assertThat(goal.isCompleted()).isFalse();
    }

    @Test
    void aGoalWithAllStepsDoneIsCompleted() {
        addStep("A", StepStatus.DONE);
        addStep("B", StepStatus.DONE);

        assertThat(goal.isCompleted()).isTrue();
    }

    @Test
    void restoreKeepsTheGivenStepOrder() {
        RoadmapStep c = restoredStep("C", StepStatus.NOT_STARTED, null);
        RoadmapStep a = restoredStep("A", StepStatus.NOT_STARTED, null);
        RoadmapStep b = restoredStep("B", StepStatus.NOT_STARTED, null);

        Goal restored = Goal.restore(GoalId.newId(), areaId, "Goal", null, null, 0, NOW, List.of(c, a, b));

        assertThat(restored.steps()).containsExactly(c, a, b);
    }

    @Test
    void restoredStepNotDoneDropsThePassedCompletedAt() {
        assertThat(restoredStep("A", StepStatus.IN_PROGRESS, NOW).completedAt()).isNull();
    }

    @Test
    void restoredDoneStepKeepsItsCompletedAt() {
        assertThat(restoredStep("A", StepStatus.DONE, NOW).completedAt()).isEqualTo(NOW);
    }

    // ---------- weekly target ----------

    @Test
    void aGoalHasNoWeeklyTargetByDefault() {
        assertThat(goal.weeklyTargetMinutes()).isNull();
    }

    @Test
    void weeklyTargetCanBeSetChangedAndRemoved() {
        goal.changeWeeklyTarget(150);
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(150);

        goal.changeWeeklyTarget(1);
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(1);
        goal.changeWeeklyTarget(10_080);
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(10_080);

        goal.changeWeeklyTarget(null);
        assertThat(goal.weeklyTargetMinutes()).isNull();
    }

    @Test
    void weeklyTargetOutsideTheRangeIsRejectedAndKeepsTheOldOne() {
        goal.changeWeeklyTarget(150);

        for (int minutes : new int[]{0, -1, 10_081}) {
            assertThatThrownBy(() -> goal.changeWeeklyTarget(minutes))
                    .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                        assertThat(error.code()).isEqualTo("out-of-range");
                        assertThat(error.args()).containsExactly("Weekly target", 1, 10_080);
                    });
        }
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(150);
    }

    @Test
    void createAndRestoreCarryTheWeeklyTarget() {
        Goal created = Goal.create(areaId, "Guitar", null, null, 90, 0, NOW);
        Goal restored = Goal.restore(GoalId.newId(), areaId, "Guitar", null, null, 90, 0, NOW, List.of());

        assertThat(created.weeklyTargetMinutes()).isEqualTo(90);
        assertThat(restored.weeklyTargetMinutes()).isEqualTo(90);
        assertThatThrownBy(() -> Goal.create(areaId, "Guitar", null, null, 0, 0, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void updateWithoutATargetKeepsTheCurrentOne() {
        goal.changeWeeklyTarget(150);

        goal.update("Renamed", null, null);

        assertThat(goal.weeklyTargetMinutes()).isEqualTo(150);
    }

    @Test
    void updateWithATargetReplacesItAtomically() {
        goal.changeWeeklyTarget(150);

        goal.update("Renamed", null, null, 200);
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(200);

        assertThatThrownBy(() -> goal.update("Again", null, null, 99_999))
                .isInstanceOf(DomainValidationException.class);
        assertThat(goal.title()).isEqualTo("Renamed");
        assertThat(goal.weeklyTargetMinutes()).isEqualTo(200);
    }
}
