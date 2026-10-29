package com.edutrack.domain.skill;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class CompetencyTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate DAY_1 = LocalDate.of(2026, 10, 27);
    private static final LocalDate DAY_2 = LocalDate.of(2026, 10, 28);

    private final GoalId goalId = GoalId.newId();

    private Competency competency() {
        return Competency.create(goalId, new CompetencyDetails("Listening", "Understand native speed", 5), 0, NOW);
    }

    private static DomainValidationException invalid(ThrowingCallable action) {
        return catchThrowableOfType(DomainValidationException.class, action);
    }

    // ---------- creation and details ----------

    @Test
    void startsAtLevelZeroWithEmptyHistoryAndNoSteps() {
        Competency competency = competency();

        assertThat(competency.name()).isEqualTo("Listening");
        assertThat(competency.description()).isEqualTo("Understand native speed");
        assertThat(competency.level()).isZero();
        assertThat(competency.targetLevel()).isEqualTo(5);
        assertThat(competency.position()).isZero();
        assertThat(competency.createdAt()).isEqualTo(NOW);
        assertThat(competency.goalId()).isEqualTo(goalId);
        assertThat(competency.history()).isEmpty();
        assertThat(competency.steps()).isEmpty();
        assertThat(competency.meetsTarget()).isFalse();
    }

    @Test
    void nameAndDescriptionAreStrippedAndBlankDescriptionBecomesNull() {
        Competency competency = Competency.create(goalId, new CompetencyDetails("  SQL  ", "   ", 3), 2, NOW);

        assertThat(competency.name()).isEqualTo("SQL");
        assertThat(competency.description()).isNull();
    }

    @Test
    void nameIsRequiredAndAtMost80Characters() {
        DomainValidationException blank = invalid(() -> Competency.create(goalId, new CompetencyDetails(" ", null, 3), 0, NOW));
        DomainValidationException tooLong = invalid(() -> Competency.create(goalId, new CompetencyDetails("x".repeat(81), null, 3), 0, NOW));

        assertThat(blank.code()).isEqualTo("required");
        assertThat(blank.args()).containsExactly("Name");
        assertThat(tooLong.code()).isEqualTo("too-long");
        assertThat(tooLong.args()).containsExactly("Name", 80);
    }

    @Test
    void descriptionIsAtMost2000Characters() {
        DomainValidationException error = invalid(() ->
                Competency.create(goalId, new CompetencyDetails("A", "x".repeat(2001), 3), 0, NOW));

        assertThat(error.code()).isEqualTo("too-long");
        assertThat(error.args()).containsExactly("Description", 2000);
    }

    @Test
    void targetLevelIsRequiredAndBetweenOneAndFive() {
        DomainValidationException missing = invalid(() -> Competency.create(goalId, new CompetencyDetails("A", null, null), 0, NOW));
        DomainValidationException zero = invalid(() -> Competency.create(goalId, new CompetencyDetails("A", null, 0), 0, NOW));
        DomainValidationException six = invalid(() -> Competency.create(goalId, new CompetencyDetails("A", null, 6), 0, NOW));

        assertThat(missing.code()).isEqualTo("required");
        assertThat(missing.args()).containsExactly("Target level");
        assertThat(zero.code()).isEqualTo("out-of-range");
        assertThat(zero.args()).containsExactly("Target level", 1, 5);
        assertThat(six.code()).isEqualTo("out-of-range");
    }

    @Test
    void negativePositionIsRejected() {
        assertThatThrownBy(() -> Competency.create(goalId, new CompetencyDetails("A", null, 3), -1, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void updateChangesTheDetailsButNotTheLevel() {
        Competency competency = competency();
        competency.changeLevel(2, DAY_1);

        competency.update(new CompetencyDetails("Listening (news)", null, 4));

        assertThat(competency.name()).isEqualTo("Listening (news)");
        assertThat(competency.description()).isNull();
        assertThat(competency.targetLevel()).isEqualTo(4);
        assertThat(competency.level()).isEqualTo(2);
        assertThat(competency.history()).hasSize(1);
    }

    @Test
    void aRejectedUpdateChangesNothing() {
        Competency competency = competency();

        assertThatThrownBy(() -> competency.update(new CompetencyDetails("New name", null, 9)))
                .isInstanceOf(DomainValidationException.class);

        assertThat(competency.name()).isEqualTo("Listening");
        assertThat(competency.targetLevel()).isEqualTo(5);
    }

    // ---------- level ----------

    @Test
    void changingTheLevelAppendsToTheHistory() {
        Competency competency = competency();

        competency.changeLevel(2, DAY_1);
        competency.changeLevel(3, DAY_2);

        assertThat(competency.level()).isEqualTo(3);
        assertThat(competency.history()).containsExactly(new LevelChange(DAY_1, 2), new LevelChange(DAY_2, 3));
    }

    @Test
    void aSecondChangeOnTheSameDayReplacesThatDaysEntry() {
        Competency competency = competency();
        competency.changeLevel(1, DAY_1);
        competency.changeLevel(2, DAY_2);

        competency.changeLevel(4, DAY_2);

        assertThat(competency.level()).isEqualTo(4);
        assertThat(competency.history()).containsExactly(new LevelChange(DAY_1, 1), new LevelChange(DAY_2, 4));
    }

    @Test
    void settingTheSameLevelAgainDoesNothing() {
        Competency competency = competency();
        competency.changeLevel(3, DAY_1);

        competency.changeLevel(3, DAY_2);

        assertThat(competency.history()).containsExactly(new LevelChange(DAY_1, 3));
    }

    @Test
    void levelMustBeBetweenZeroAndFive() {
        Competency competency = competency();

        DomainValidationException negative = invalid(() -> competency.changeLevel(-1, DAY_1));
        DomainValidationException tooHigh = invalid(() -> competency.changeLevel(6, DAY_1));

        assertThat(negative.code()).isEqualTo("out-of-range");
        assertThat(negative.args()).containsExactly("Level", 0, 5);
        assertThat(tooHigh.code()).isEqualTo("out-of-range");
        assertThat(competency.level()).isZero();
        assertThat(competency.history()).isEmpty();
    }

    @Test
    void goingBackToLevelZeroIsRecorded() {
        Competency competency = competency();
        competency.changeLevel(2, DAY_1);

        competency.changeLevel(0, DAY_2);

        assertThat(competency.history()).containsExactly(new LevelChange(DAY_1, 2), new LevelChange(DAY_2, 0));
    }

    @Test
    void meetsTargetFromTheTargetLevelUp() {
        Competency competency = Competency.create(goalId, new CompetencyDetails("SQL", null, 3), 0, NOW);

        competency.changeLevel(2, DAY_1);
        assertThat(competency.meetsTarget()).isFalse();
        competency.changeLevel(3, DAY_2);
        assertThat(competency.meetsTarget()).isTrue();
        competency.changeLevel(5, DAY_2);
        assertThat(competency.meetsTarget()).isTrue();
    }

    // ---------- steps ----------

    @Test
    void linkingStepsAddsToThoseAlreadyLinked() {
        Competency competency = competency();
        StepId first = StepId.newId();
        StepId second = StepId.newId();

        competency.linkSteps(Set.of(first));
        competency.linkSteps(Set.of(first, second));

        assertThat(competency.steps()).containsExactlyInAnyOrder(first, second);
    }

    @Test
    void unlinkingAStepRemovesOnlyThatStep() {
        Competency competency = competency();
        StepId first = StepId.newId();
        StepId second = StepId.newId();
        competency.linkSteps(Set.of(first, second));

        competency.unlinkStep(first);
        competency.unlinkStep(StepId.newId());

        assertThat(competency.steps()).containsExactly(second);
    }

    @Test
    void settingTheStepsReplacesThem() {
        Competency competency = competency();
        StepId old = StepId.newId();
        StepId kept = StepId.newId();
        StepId added = StepId.newId();
        competency.linkSteps(Set.of(old, kept));

        competency.setSteps(Set.of(kept, added));

        assertThat(competency.steps()).containsExactlyInAnyOrder(kept, added);
    }

    @Test
    void exposedCollectionsAreReadOnly() {
        Competency competency = competency();

        assertThatThrownBy(() -> competency.steps().add(StepId.newId())).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> competency.history().add(new LevelChange(DAY_1, 1))).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void movingChangesThePositionAndRejectsNegatives() {
        Competency competency = competency();

        competency.moveTo(3);

        assertThat(competency.position()).isEqualTo(3);
        assertThatThrownBy(() -> competency.moveTo(-1)).isInstanceOf(DomainValidationException.class);
    }

    // ---------- restore ----------

    @Test
    void restoreKeepsEverythingAndSortsTheHistory() {
        CompetencyId id = CompetencyId.newId();
        StepId step = StepId.newId();

        Competency restored = Competency.restore(id, goalId, new CompetencyDetails("SQL", null, 4), 3, 2, NOW,
                List.of(new LevelChange(DAY_2, 3), new LevelChange(DAY_1, 1)), Set.of(step));

        assertThat(restored.id()).isEqualTo(id);
        assertThat(restored.level()).isEqualTo(3);
        assertThat(restored.position()).isEqualTo(2);
        assertThat(restored.history()).containsExactly(new LevelChange(DAY_1, 1), new LevelChange(DAY_2, 3));
        assertThat(restored.steps()).containsExactly(step);
    }

    @Test
    void equalityIsByIdentifier() {
        CompetencyId id = CompetencyId.newId();
        CompetencyDetails details = new CompetencyDetails("SQL", null, 4);

        Competency a = Competency.restore(id, goalId, details, 0, 0, NOW, List.of(), Set.of());
        Competency b = Competency.restore(id, goalId, details, 2, 1, NOW, List.of(), Set.of());

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(competency());
    }
}
