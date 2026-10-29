package com.edutrack.domain.record;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LearningRecordTest {

    private static final Instant T0 = Instant.parse("2026-10-27T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-10-27T11:00:00Z");
    private static final Instant T2 = Instant.parse("2026-10-27T12:00:00Z");
    private static final Instant T3 = Instant.parse("2026-10-27T13:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final RecordOwner owner = RecordOwner.ofStep(StepId.newId());
    private final LearningRecord record = LearningRecord.create(owner, T0);

    private static ResourceDetails resource(String title, String url) {
        return new ResourceDetails(title, url, ResourceType.COURSE);
    }

    private static AssessmentDetails result(String score, String max) {
        return new AssessmentDetails("Mock test", new BigDecimal(score), max == null ? null : new BigDecimal(max),
                LocalDate.of(2026, 10, 12), null);
    }

    // ---------- creation ----------

    @Test
    void newRecordIsEmpty() {
        assertThat(record.owner()).isEqualTo(owner);
        assertThat(record.notes()).isEmpty();
        assertThat(record.updatedAt()).isEqualTo(T0);
        assertThat(record.resources()).isEmpty();
        assertThat(record.exercises()).isEmpty();
        assertThat(record.assessments()).isEmpty();
        assertThat(record.summary()).isEqualTo(RecordSummary.EMPTY);
        assertThat(record.summary().isEmpty()).isTrue();
    }

    @Test
    void goalOwnedRecordIsAllowed() {
        RecordOwner goalOwner = RecordOwner.ofGoal(GoalId.newId());
        assertThat(LearningRecord.create(goalOwner, T0).owner().isStep()).isFalse();
        assertThat(owner.isStep()).isTrue();
    }

    // ---------- notes ----------

    @Test
    void notesAreStoredVerbatim() {
        String markdown = "\n  # Title  \n\n- item\n   ";

        record.updateNotes(markdown, T1);

        assertThat(record.notes()).isEqualTo(markdown);
    }

    @Test
    void blankNotesAreAllowed() {
        record.updateNotes("text", T1);
        record.updateNotes("   ", T2);
        assertThat(record.notes()).isEqualTo("   ");
        record.updateNotes(null, T3);
        assertThat(record.notes()).isEmpty();
    }

    @Test
    void notesAtTheLimitAreAcceptedAndAboveAreRejected() {
        record.updateNotes("a".repeat(200_000), T1);
        assertThat(record.notes()).hasSize(200_000);

        assertThatThrownBy(() -> record.updateNotes("a".repeat(200_001), T2))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("200000");
        assertThat(record.notes()).hasSize(200_000);
        assertThat(record.updatedAt()).isEqualTo(T1);
    }

    @Test
    void everyChangeAdvancesUpdatedAt() {
        record.updateNotes("n", T1);
        assertThat(record.updatedAt()).isEqualTo(T1);

        Resource res = record.addResource(resource("Course", null), T2);
        assertThat(record.updatedAt()).isEqualTo(T2);
        record.updateResource(res.id(), resource("Course 2", null), T3);
        assertThat(record.updatedAt()).isEqualTo(T3);
        record.markResourceReviewed(res.id(), true, T1);
        assertThat(record.updatedAt()).isEqualTo(T1);
        record.removeResource(res.id(), T2);
        assertThat(record.updatedAt()).isEqualTo(T2);

        Exercise ex = record.addExercise("Essay", T3);
        assertThat(record.updatedAt()).isEqualTo(T3);
        record.renameExercise(ex.id(), "Essays", T1);
        assertThat(record.updatedAt()).isEqualTo(T1);
        record.markExerciseDone(ex.id(), true, T2);
        assertThat(record.updatedAt()).isEqualTo(T2);
        record.addExercise("Other", T3);
        record.moveExercise(ex.id(), MoveDirection.DOWN, T1);
        assertThat(record.updatedAt()).isEqualTo(T1);
        record.removeExercise(ex.id(), T2);
        assertThat(record.updatedAt()).isEqualTo(T2);

        Assessment a = record.addAssessment(result("5", "10"), TODAY, T3);
        assertThat(record.updatedAt()).isEqualTo(T3);
        record.updateAssessment(a.id(), result("6", "10"), TODAY, T1);
        assertThat(record.updatedAt()).isEqualTo(T1);
        record.removeAssessment(a.id(), T2);
        assertThat(record.updatedAt()).isEqualTo(T2);
    }

    // ---------- resources ----------

    @Test
    void addedResourceKeepsItsValuesAndIsNotReviewed() {
        Resource res = record.addResource(
                new ResourceDetails("  BBC Learning English ", "bbc.co.uk/learningenglish", ResourceType.ARTICLE), T1);

        assertThat(record.resources()).containsExactly(res);
        assertThat(res.title()).isEqualTo("BBC Learning English");
        assertThat(res.url()).isEqualTo("https://bbc.co.uk/learningenglish");
        assertThat(res.type()).isEqualTo(ResourceType.ARTICLE);
        assertThat(res.reviewed()).isFalse();
    }

    @Test
    void resourceLinkIsOptional() {
        assertThat(record.addResource(resource("Printed book", null), T1).url()).isNull();
        assertThat(record.addResource(resource("Book 2", "  "), T1).url()).isNull();
    }

    @Test
    void resourceTitleIsRequiredAndLimitedTo160() {
        assertThatThrownBy(() -> record.addResource(resource("  ", null), T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Title is required.");
        assertThat(record.addResource(resource("a".repeat(160), null), T1).title()).hasSize(160);
        assertThatThrownBy(() -> record.addResource(resource("a".repeat(161), null), T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("160");
    }

    @Test
    void resourceTypeIsRequired() {
        assertThatThrownBy(() -> record.addResource(new ResourceDetails("X", null, null), T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Type is required.");
    }

    @Test
    void invalidResourceLinkIsRejected() {
        assertThatThrownBy(() -> record.addResource(resource("X", "ftp://example.com"), T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Link must be a web address");
        assertThat(record.resources()).isEmpty();
        assertThat(record.updatedAt()).isEqualTo(T0);
    }

    @Test
    void resourceCanBeMarkedReviewedAndBack() {
        Resource res = record.addResource(resource("X", null), T1);
        record.markResourceReviewed(res.id(), true, T2);
        assertThat(res.reviewed()).isTrue();
        record.markResourceReviewed(res.id(), false, T3);
        assertThat(res.reviewed()).isFalse();
    }

    @Test
    void updatingAResourceChangesAllFields() {
        Resource res = record.addResource(resource("Old", null), T1);
        record.markResourceReviewed(res.id(), true, T1);

        record.updateResource(res.id(), new ResourceDetails("New", "example.com", ResourceType.VIDEO), T2);

        assertThat(res.title()).isEqualTo("New");
        assertThat(res.url()).isEqualTo("https://example.com");
        assertThat(res.type()).isEqualTo(ResourceType.VIDEO);
        assertThat(res.reviewed()).isTrue();
    }

    @Test
    void rejectedResourceUpdateChangesNothing() {
        Resource res = record.addResource(new ResourceDetails("Old", "example.com", ResourceType.BOOK), T1);

        assertThatThrownBy(() -> record.updateResource(res.id(),
                new ResourceDetails("New", "ftp://bad", ResourceType.VIDEO), T2))
                .isInstanceOf(DomainValidationException.class);

        assertThat(res.title()).isEqualTo("Old");
        assertThat(res.url()).isEqualTo("https://example.com");
        assertThat(res.type()).isEqualTo(ResourceType.BOOK);
        assertThat(record.updatedAt()).isEqualTo(T1);
    }

    @Test
    void resourceCanBeRemoved() {
        Resource first = record.addResource(resource("A", null), T1);
        Resource second = record.addResource(resource("B", null), T1);

        record.removeResource(first.id(), T2);

        assertThat(record.resources()).containsExactly(second);
    }

    // ---------- exercises ----------

    @Test
    void exercisesAreAddedInOrderAndNotDone() {
        Exercise a = record.addExercise("  Write essays ", T1);
        Exercise b = record.addExercise("Speak", T1);

        assertThat(record.exercises()).containsExactly(a, b);
        assertThat(a.title()).isEqualTo("Write essays");
        assertThat(a.done()).isFalse();
        assertThat(a.doneAt()).isNull();
    }

    @Test
    void exerciseTitleIsRequiredAndLimitedTo200() {
        assertThatThrownBy(() -> record.addExercise(" ", T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Exercise is required.");
        assertThat(record.addExercise("a".repeat(200), T1).title()).hasSize(200);
        assertThatThrownBy(() -> record.addExercise("a".repeat(201), T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("200");
    }

    @Test
    void exerciseCanBeRenamed() {
        Exercise ex = record.addExercise("Old", T1);
        record.renameExercise(ex.id(), "New", T2);
        assertThat(ex.title()).isEqualTo("New");
        assertThatThrownBy(() -> record.renameExercise(ex.id(), "", T3))
                .isInstanceOf(DomainValidationException.class);
        assertThat(ex.title()).isEqualTo("New");
    }

    @Test
    void tickingRecordsWhenAndUntickingClearsIt() {
        Exercise ex = record.addExercise("Essay", T1);

        record.markExerciseDone(ex.id(), true, T2);
        assertThat(ex.done()).isTrue();
        assertThat(ex.doneAt()).isEqualTo(T2);

        record.markExerciseDone(ex.id(), false, T3);
        assertThat(ex.done()).isFalse();
        assertThat(ex.doneAt()).isNull();
    }

    @Test
    void tickingTwiceKeepsTheFirstDate() {
        Exercise ex = record.addExercise("Essay", T1);

        record.markExerciseDone(ex.id(), true, T2);
        record.markExerciseDone(ex.id(), true, T3);

        assertThat(ex.doneAt()).isEqualTo(T2);
    }

    @Test
    void exercisesMoveUpAndDown() {
        Exercise a = record.addExercise("A", T1);
        Exercise b = record.addExercise("B", T1);
        Exercise c = record.addExercise("C", T1);

        record.moveExercise(c.id(), MoveDirection.UP, T2);
        assertThat(record.exercises()).containsExactly(a, c, b);

        record.moveExercise(a.id(), MoveDirection.DOWN, T2);
        assertThat(record.exercises()).containsExactly(c, a, b);
    }

    @Test
    void movingPastEitherEndDoesNothing() {
        Exercise a = record.addExercise("A", T1);
        Exercise b = record.addExercise("B", T1);

        record.moveExercise(a.id(), MoveDirection.UP, T2);
        record.moveExercise(b.id(), MoveDirection.DOWN, T3);

        assertThat(record.exercises()).containsExactly(a, b);
        assertThat(record.updatedAt()).isEqualTo(T1);
    }

    @Test
    void exerciseCanBeRemoved() {
        Exercise a = record.addExercise("A", T1);
        Exercise b = record.addExercise("B", T1);
        record.removeExercise(a.id(), T2);
        assertThat(record.exercises()).containsExactly(b);
    }

    // ---------- assessments ----------

    @Test
    void assessmentKeepsItsValues() {
        Assessment a = record.addAssessment(new AssessmentDetails(" IELTS mock ", new BigDecimal("6.5"),
                new BigDecimal("9"), LocalDate.of(2026, 10, 12), " Listening was hard "), TODAY, T1);

        assertThat(record.assessments()).containsExactly(a);
        assertThat(a.title()).isEqualTo("IELTS mock");
        assertThat(a.score()).isEqualTo(new BigDecimal("6.50"));
        assertThat(a.maxScore()).isEqualTo(new BigDecimal("9.00"));
        assertThat(a.takenOn()).isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(a.note()).isEqualTo("Listening was hard");
    }

    @Test
    void assessmentTitleDateAndScoreAreRequired() {
        LocalDate date = LocalDate.of(2026, 10, 12);
        assertThatThrownBy(() -> record.addAssessment(new AssessmentDetails(" ", BigDecimal.ONE, null, date, null), TODAY, T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Title is required.");
        assertThatThrownBy(() -> record.addAssessment(new AssessmentDetails("X", null, null, date, null), TODAY, T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Score is required.");
        assertThatThrownBy(() -> record.addAssessment(new AssessmentDetails("X", BigDecimal.ONE, null, null, null), TODAY, T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Date is required.");
    }

    @Test
    void negativeScoreIsRejectedAndZeroAccepted() {
        assertThatThrownBy(() -> record.addAssessment(result("-1", null), TODAY, T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("Score cannot be negative.");
        assertThat(record.addAssessment(result("0", "10"), TODAY, T1).score()).isEqualByComparingTo("0");
    }

    @Test
    void scoreIsRoundedToTwoDecimals() {
        assertThat(record.addAssessment(result("6.666", null), TODAY, T1).score()).isEqualTo(new BigDecimal("6.67"));
    }

    @Test
    void maximumIsOptionalButMustBePositive() {
        assertThat(record.addAssessment(result("5", null), TODAY, T1).maxScore()).isNull();
        assertThatThrownBy(() -> record.addAssessment(result("0", "0"), TODAY, T1))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Maximum score must be greater than 0.");
        assertThatThrownBy(() -> record.addAssessment(result("0", "-1"), TODAY, T1))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("Maximum score cannot be negative.");
    }

    @Test
    void scoreCannotBeHigherThanTheMaximum() {
        assertThat(record.addAssessment(result("9", "9"), TODAY, T1).score()).isEqualByComparingTo("9");
        assertThatThrownBy(() -> record.addAssessment(result("9.01", "9"), TODAY, T1))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Score cannot be higher than the maximum score.");
    }

    @Test
    void assessmentNoteIsLimitedTo500() {
        LocalDate date = LocalDate.of(2026, 10, 12);
        assertThat(record.addAssessment(new AssessmentDetails("X", BigDecimal.ONE, null, date, "a".repeat(500)), TODAY, T1)
                .note()).hasSize(500);
        assertThatThrownBy(() -> record.addAssessment(
                new AssessmentDetails("X", BigDecimal.ONE, null, date, "a".repeat(501)), TODAY, T1))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("500");
    }

    @Test
    void percentageIsRoundedAndEmptyWithoutMaximum() {
        Assessment withMax = record.addAssessment(result("6.5", "9"), TODAY, T1);
        Assessment withoutMax = record.addAssessment(result("6.5", null), TODAY, T1);

        assertThat(withMax.percentage()).isPresent();
        assertThat(withMax.percentage().get().percent()).isEqualByComparingTo("72.22");
        assertThat(withMax.percentage().get().rounded()).isEqualTo(72);
        assertThat(withoutMax.percentage()).isEmpty();
    }

    @Test
    void resultsCannotBeDatedInTheFuture() {
        AssessmentDetails tomorrow = new AssessmentDetails("Mock test", BigDecimal.ONE, null, TODAY.plusDays(1), null);
        AssessmentDetails today = new AssessmentDetails("Mock test", BigDecimal.ONE, null, TODAY, null);

        assertThatThrownBy(() -> record.addAssessment(tomorrow, TODAY, T1))
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Date cannot be in the future.");
        assertThat(record.assessments()).isEmpty();

        Assessment a = record.addAssessment(today, TODAY, T1);
        assertThatThrownBy(() -> record.updateAssessment(a.id(), tomorrow, TODAY, T2))
                .isInstanceOf(DomainValidationException.class);
        assertThat(a.takenOn()).isEqualTo(TODAY);
    }

    @Test
    void assessmentCanBeUpdatedAllOrNothingAndRemoved() {
        Assessment a = record.addAssessment(result("5", "10"), TODAY, T1);

        record.updateAssessment(a.id(), result("8", "10"), TODAY, T2);
        assertThat(a.score()).isEqualByComparingTo("8");

        assertThatThrownBy(() -> record.updateAssessment(a.id(), result("11", "10"), TODAY, T3))
                .isInstanceOf(DomainValidationException.class);
        assertThat(a.score()).isEqualByComparingTo("8");
        assertThat(record.updatedAt()).isEqualTo(T2);

        record.removeAssessment(a.id(), T3);
        assertThat(record.assessments()).isEmpty();
    }

    // ---------- summary ----------

    @Test
    void summaryCountsEverything() {
        record.updateNotes("# Notes", T1);
        record.addResource(resource("A", null), T1);
        record.addResource(resource("B", null), T1);
        Exercise done = record.addExercise("One", T1);
        record.addExercise("Two", T1);
        record.addExercise("Three", T1);
        record.markExerciseDone(done.id(), true, T2);
        record.addAssessment(result("5", "10"), TODAY, T1);

        assertThat(record.summary()).isEqualTo(new RecordSummary(true, 2, 1, 3, 1));
        assertThat(record.summary().isEmpty()).isFalse();
    }

    @Test
    void whitespaceOnlyNotesDoNotCount() {
        record.updateNotes(" \n\t ", T1);
        assertThat(record.summary().hasNotes()).isFalse();
        assertThat(record.summary().isEmpty()).isTrue();
    }

    // ---------- unknown ids ----------

    @Test
    void unknownIdsAreRejected() {
        assertThatThrownBy(() -> record.updateResource(ResourceId.newId(), resource("X", null), T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.markResourceReviewed(ResourceId.newId(), true, T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.removeResource(ResourceId.newId(), T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.renameExercise(ExerciseId.newId(), "X", T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.markExerciseDone(ExerciseId.newId(), true, T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.moveExercise(ExerciseId.newId(), MoveDirection.UP, T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.removeExercise(ExerciseId.newId(), T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.updateAssessment(AssessmentId.newId(), result("1", null), TODAY, T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> record.removeAssessment(AssessmentId.newId(), T1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(record.updatedAt()).isEqualTo(T0);
    }

    // ---------- restore ----------

    @Test
    void restoreKeepsTheGivenState() {
        Exercise ex = Exercise.restore(ExerciseId.newId(), "Essay", true, T1);
        Exercise open = Exercise.restore(ExerciseId.newId(), "Open", false, T1);
        LearningRecord restored = LearningRecord.restore(LearningRecordId.newId(), owner, "notes", T2,
                List.of(Resource.restore(ResourceId.newId(), "R", null, ResourceType.BOOK, true)),
                List.of(ex, open), List.of());

        assertThat(restored.notes()).isEqualTo("notes");
        assertThat(restored.updatedAt()).isEqualTo(T2);
        assertThat(restored.resources().getFirst().reviewed()).isTrue();
        assertThat(ex.doneAt()).isEqualTo(T1);
        assertThat(open.doneAt()).isNull();
        assertThat(restored.exercises()).containsExactly(ex, open);
    }
}
