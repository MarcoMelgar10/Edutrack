package com.edutrack.domain.practice;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PracticeRoutineTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");

    private final GoalId goalId = GoalId.newId();

    private PracticeRoutine create(String title, Cadence cadence, Integer timesPerWeek, Integer targetMinutes) {
        return PracticeRoutine.create(goalId, new RoutineDetails(null, title, cadence, timesPerWeek, targetMinutes), 0, NOW);
    }

    @Test
    void createStartsActiveAndKeepsTheContent() {
        StepId stepId = StepId.newId();

        PracticeRoutine routine = PracticeRoutine.create(goalId,
                new RoutineDetails(stepId, "  Shadowing  ", Cadence.WEEKLY, 5, 20), 3, NOW);

        assertThat(routine.goalId()).isEqualTo(goalId);
        assertThat(routine.stepId()).isEqualTo(stepId);
        assertThat(routine.title()).isEqualTo("Shadowing");
        assertThat(routine.cadence()).isEqualTo(Cadence.WEEKLY);
        assertThat(routine.timesPerWeek()).isEqualTo(5);
        assertThat(routine.targetMinutes()).isEqualTo(20);
        assertThat(routine.active()).isTrue();
        assertThat(routine.position()).isEqualTo(3);
        assertThat(routine.createdAt()).isEqualTo(NOW);
    }

    @Test
    void titleIsRequired() {
        assertThatThrownBy(() -> create("  ", Cadence.DAILY, null, 20))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Title is required.");
    }

    @Test
    void titleOf121CharactersIsRejected() {
        assertThatThrownBy(() -> create("a".repeat(121), Cadence.DAILY, null, 20))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("too-long"));
    }

    @Test
    void cadenceIsRequired() {
        assertThatThrownBy(() -> create("Vocabulary", null, 3, 20))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Cadence is required.");
    }

    @Test
    void aDailyRoutineIsAlwaysSevenTimesAWeekWhateverWasTyped() {
        assertThat(create("Vocabulary", Cadence.DAILY, null, 15).timesPerWeek()).isEqualTo(7);
        assertThat(create("Vocabulary", Cadence.DAILY, 2, 15).timesPerWeek()).isEqualTo(7);
        assertThat(create("Vocabulary", Cadence.DAILY, 99, 15).timesPerWeek()).isEqualTo(7);
    }

    @Test
    void aWeeklyRoutineNeedsItsTimesPerWeek() {
        assertThatThrownBy(() -> create("Vocabulary", Cadence.WEEKLY, null, 15))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Times per week is required.");
    }

    @Test
    void aWeeklyRoutineAcceptsOneToSevenTimes() {
        assertThat(create("Vocabulary", Cadence.WEEKLY, 1, 15).timesPerWeek()).isEqualTo(1);
        assertThat(create("Vocabulary", Cadence.WEEKLY, 7, 15).timesPerWeek()).isEqualTo(7);
    }

    @Test
    void timesPerWeekOutsideOneToSevenIsRejected() {
        for (int times : new int[]{0, 8, -1}) {
            assertThatThrownBy(() -> create("Vocabulary", Cadence.WEEKLY, times, 15))
                    .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                        assertThat(error.code()).isEqualTo("out-of-range");
                        assertThat(error.args()).containsExactly("Times per week", 1, 7);
                    });
        }
    }

    @Test
    void targetMinutesAreRequired() {
        assertThatThrownBy(() -> create("Vocabulary", Cadence.DAILY, null, null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Target minutes is required.");
    }

    @Test
    void targetMinutesBetweenOneAndSixHundredAreAccepted() {
        assertThat(create("Vocabulary", Cadence.DAILY, null, 1).targetMinutes()).isEqualTo(1);
        assertThat(create("Vocabulary", Cadence.DAILY, null, 600).targetMinutes()).isEqualTo(600);
    }

    @Test
    void targetMinutesOutsideTheRangeAreRejected() {
        for (int minutes : new int[]{0, 601}) {
            assertThatThrownBy(() -> create("Vocabulary", Cadence.DAILY, null, minutes))
                    .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                        assertThat(error.code()).isEqualTo("out-of-range");
                        assertThat(error.args()).containsExactly("Target minutes", 1, 600);
                    });
        }
    }

    @Test
    void updateReplacesTheContent() {
        PracticeRoutine routine = create("Vocabulary", Cadence.DAILY, null, 15);
        StepId stepId = StepId.newId();

        routine.update(new RoutineDetails(stepId, "Speaking", Cadence.WEEKLY, 3, 40));

        assertThat(routine.stepId()).isEqualTo(stepId);
        assertThat(routine.title()).isEqualTo("Speaking");
        assertThat(routine.cadence()).isEqualTo(Cadence.WEEKLY);
        assertThat(routine.timesPerWeek()).isEqualTo(3);
        assertThat(routine.targetMinutes()).isEqualTo(40);
    }

    @Test
    void aRejectedUpdateChangesNothing() {
        PracticeRoutine routine = create("Vocabulary", Cadence.WEEKLY, 3, 15);

        assertThatThrownBy(() -> routine.update(new RoutineDetails(StepId.newId(), "Speaking", Cadence.WEEKLY, 3, 9000)))
                .isInstanceOf(DomainValidationException.class);

        assertThat(routine.title()).isEqualTo("Vocabulary");
        assertThat(routine.stepId()).isNull();
        assertThat(routine.targetMinutes()).isEqualTo(15);
    }

    @Test
    void pauseAndResumeToggleActive() {
        PracticeRoutine routine = create("Vocabulary", Cadence.DAILY, null, 15);

        routine.pause();
        assertThat(routine.active()).isFalse();
        routine.resume();
        assertThat(routine.active()).isTrue();
    }

    @Test
    void moveToRejectsNegativePositions() {
        PracticeRoutine routine = create("Vocabulary", Cadence.DAILY, null, 15);

        routine.moveTo(4);

        assertThat(routine.position()).isEqualTo(4);
        assertThatThrownBy(() -> routine.moveTo(-1)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void restoreKeepsAPausedRoutinePaused() {
        PracticeRoutineId id = PracticeRoutineId.newId();

        PracticeRoutine restored = PracticeRoutine.restore(id, goalId, null, "Vocabulary", Cadence.DAILY, 7, 15,
                false, 2, NOW);

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.active()).isFalse();
        assertThat(restored.position()).isEqualTo(2);
    }
}
