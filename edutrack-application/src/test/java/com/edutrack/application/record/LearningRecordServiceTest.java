package com.edutrack.application.record;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryLearningRecordRepository;
import com.edutrack.application.support.InMemoryMeasureRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.measure.Measure;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.ResourceDetails;
import com.edutrack.domain.record.ResourceType;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LearningRecordServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final InMemoryLearningRecordRepository records = new InMemoryLearningRecordRepository();
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryMeasureRepository measures = new InMemoryMeasureRepository();
    private final LearningRecordService service =
            new LearningRecordService(records, goals, measures, Clock.fixed(NOW, ZoneOffset.UTC));
    private final RecordOwner step = RecordOwner.ofStep(StepId.newId());

    private static AssessmentDetails result(String title, String score, String max, LocalDate date) {
        return new AssessmentDetails(title, new BigDecimal(score), max == null ? null : new BigDecimal(max), date, null);
    }

    @Test
    void openingAnOwnerWithoutRecordReturnsAnEmptyViewAndDoesNotSave() {
        RecordView view = service.open(step);

        assertThat(view.owner()).isEqualTo(step);
        assertThat(view.notes()).isEmpty();
        assertThat(view.updatedAt()).isNull();
        assertThat(view.resources()).isEmpty();
        assertThat(view.exercises()).isEmpty();
        assertThat(view.results()).isEmpty();
        assertThat(view.summary()).isEqualTo(RecordSummary.EMPTY);
        assertThat(records.saveCount()).isZero();
        assertThat(records.size()).isZero();
    }

    @Test
    void firstChangeCreatesAndSavesTheRecord() {
        RecordView view = service.saveNotes(step, "# Verb tenses");

        assertThat(records.saveCount()).isEqualTo(1);
        assertThat(records.findByOwner(step)).isPresent();
        assertThat(view.notes()).isEqualTo("# Verb tenses");
        assertThat(view.updatedAt()).isEqualTo(NOW);
        assertThat(view.summary().hasNotes()).isTrue();
        assertThat(service.open(step).notes()).isEqualTo("# Verb tenses");
    }

    @Test
    void laterChangesReuseTheSameRecord() {
        service.saveNotes(step, "a");
        service.addExercise(step, "Essay");

        assertThat(records.size()).isEqualTo(1);
        assertThat(records.saveCount()).isEqualTo(2);
    }

    @Test
    void goalAndStepRecordsAreIndependent() {
        RecordOwner goal = RecordOwner.ofGoal(GoalId.newId());
        service.addResult(goal, result("Final exam", "80", "100", LocalDate.of(2026, 10, 1)));

        assertThat(service.open(step).results()).isEmpty();
        assertThat(service.open(goal).results()).hasSize(1);
    }

    @Test
    void resourceOperationsReturnTheRefreshedView() {
        RecordView added = service.addResource(step, new ResourceDetails("BBC", "bbc.co.uk", ResourceType.ARTICLE));
        var id = added.resources().getFirst().id();
        assertThat(added.resources().getFirst().url()).isEqualTo("https://bbc.co.uk");
        assertThat(added.summary().resourceCount()).isEqualTo(1);

        RecordView updated = service.updateResource(step, id, new ResourceDetails("BBC 2", null, ResourceType.VIDEO));
        assertThat(updated.resources().getFirst().title()).isEqualTo("BBC 2");
        assertThat(updated.resources().getFirst().url()).isNull();
        assertThat(updated.resources().getFirst().type()).isEqualTo(ResourceType.VIDEO);

        assertThat(service.markResourceReviewed(step, id, true).resources().getFirst().reviewed()).isTrue();
        assertThat(service.removeResource(step, id).resources()).isEmpty();
    }

    @Test
    void exerciseOperationsReturnTheRefreshedView() {
        service.addExercise(step, "A");
        RecordView view = service.addExercise(step, "B");
        var a = view.exercises().get(0).id();
        var b = view.exercises().get(1).id();
        assertThat(view.summary().exercisesTotal()).isEqualTo(2);

        RecordView moved = service.moveExercise(step, b, MoveDirection.UP);
        assertThat(moved.exercises()).extracting(RecordView.ExerciseView::title).containsExactly("B", "A");

        RecordView ticked = service.markExerciseDone(step, a, true);
        assertThat(ticked.exercises().get(1).done()).isTrue();
        assertThat(ticked.exercises().get(1).doneAt()).isEqualTo(NOW);
        assertThat(ticked.summary().exercisesDone()).isEqualTo(1);

        RecordView renamed = service.renameExercise(step, a, "A2");
        assertThat(renamed.exercises().get(1).title()).isEqualTo("A2");

        assertThat(service.removeExercise(step, a).exercises()).extracting(RecordView.ExerciseView::title)
                .containsExactly("B");
    }

    @Test
    void resultsAreListedNewestFirstWithPercentage() {
        service.addResult(step, result("Old", "5", "10", LocalDate.of(2026, 1, 10)));
        service.addResult(step, result("New", "6.5", "9", LocalDate.of(2026, 9, 10)));
        RecordView view = service.addResult(step, result("Middle", "7", null, LocalDate.of(2026, 5, 10)));

        assertThat(view.results()).extracting(RecordView.ResultView::title).containsExactly("New", "Middle", "Old");
        assertThat(view.results().get(0).percentage().rounded()).isEqualTo(72);
        assertThat(view.results().get(1).percentage()).isNull();
        assertThat(view.summary().resultCount()).isEqualTo(3);
    }

    @Test
    void resultCanBeUpdatedAndRemoved() {
        RecordView view = service.addResult(step, result("Test", "5", "10", LocalDate.of(2026, 1, 10)));
        var id = view.results().getFirst().id();

        RecordView updated = service.updateResult(step, id, result("Test 2", "8", "10", LocalDate.of(2026, 2, 1)));
        assertThat(updated.results().getFirst().title()).isEqualTo("Test 2");
        assertThat(updated.results().getFirst().score()).isEqualByComparingTo("8");

        assertThat(service.removeResult(step, id).results()).isEmpty();
    }

    @Test
    void invalidInputIsRejectedAndNothingIsSaved() {
        assertThatThrownBy(() -> service.addExercise(step, " "))
                .isInstanceOf(DomainValidationException.class);
        assertThat(records.size()).isZero();
        assertThat(records.saveCount()).isZero();
    }

    // ---------- results linked to a measure ----------

    private Goal goalWithStep() {
        Goal goal = Goal.create(AreaId.newId(), "IELTS 7", null, null, 0, NOW);
        goal.addStep(new StepDetails("Mock exams", null, null), StepStatus.NOT_STARTED, NOW);
        return goals.save(goal);
    }

    private Measure measure(Goal goal, String max) {
        return measures.save(Measure.create(goal.id(), new MeasureDetails("IELTS band", "band",
                max == null ? null : new BigDecimal(max), null, null, null, null), 0, NOW));
    }

    private static AssessmentDetails linked(MeasureId measure, String part, String score, LocalDate date) {
        return new AssessmentDetails("IELTS mock", new BigDecimal(score), null, date, null, measure, part);
    }

    @Test
    void aStepResultCanBeRecordedAgainstAMeasureOfItsGoal() {
        Goal goal = goalWithStep();
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        Measure band = measure(goal, "9");

        RecordView view = service.addResult(owner, linked(band.id(), "Listening", "6.5", LocalDate.of(2026, 10, 1)));

        assertThat(view.results()).hasSize(1);
        assertThat(view.results().getFirst().measureId()).isEqualTo(band.id());
        assertThat(view.results().getFirst().part()).isEqualTo("Listening");
        assertThat(records.resultsForMeasure(band.id())).hasSize(1);
    }

    @Test
    void aGoalResultCanBeRecordedAgainstAMeasureOfThatGoal() {
        Goal goal = goalWithStep();
        Measure band = measure(goal, null);

        RecordView view = service.addResult(RecordOwner.ofGoal(goal.id()), linked(band.id(), null, "7", LocalDate.of(2026, 10, 1)));

        assertThat(view.results().getFirst().measureId()).isEqualTo(band.id());
    }

    @Test
    void theScoreCannotExceedTheMeasuresMaximum() {
        Goal goal = goalWithStep();
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        Measure band = measure(goal, "9");

        assertThatThrownBy(() -> service.addResult(owner, linked(band.id(), null, "9.5", LocalDate.of(2026, 10, 1))))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("score-above-max"));
        assertThat(records.size()).isZero();
        assertThat(service.addResult(owner, linked(band.id(), null, "9", LocalDate.of(2026, 10, 1))).results()).hasSize(1);
    }

    @Test
    void updatingAResultAlsoChecksTheMeasuresMaximum() {
        Goal goal = goalWithStep();
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        Measure band = measure(goal, "9");
        var id = service.addResult(owner, linked(band.id(), null, "6", LocalDate.of(2026, 10, 1))).results().getFirst().id();

        assertThatThrownBy(() -> service.updateResult(owner, id, linked(band.id(), null, "10", LocalDate.of(2026, 10, 1))))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("score-above-max"));
        assertThat(service.open(owner).results().getFirst().score()).isEqualByComparingTo("6");
    }

    @Test
    void aMeasureWithoutMaximumAcceptsAnyScore() {
        Goal goal = goalWithStep();
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        Measure words = measure(goal, null);

        assertThat(service.addResult(owner, linked(words.id(), null, "250", LocalDate.of(2026, 10, 1))).results()).hasSize(1);
    }

    @Test
    void aMeasureOfAnotherGoalIsRejected() {
        Goal goal = goalWithStep();
        Goal other = goalWithStep();
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        Measure foreign = measure(other, "9");

        assertThatThrownBy(() -> service.addResult(owner, linked(foreign.id(), null, "5", LocalDate.of(2026, 10, 1))))
                .isInstanceOf(NotFoundException.class);
        assertThat(records.size()).isZero();
    }

    @Test
    void anUnknownMeasureIsRejected() {
        Goal goal = goalWithStep();

        assertThatThrownBy(() -> service.addResult(RecordOwner.ofGoal(goal.id()),
                linked(MeasureId.newId(), null, "5", LocalDate.of(2026, 10, 1))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void aStepThatBelongsToNoGoalCannotCarryAMeasuredResult() {
        Goal goal = goalWithStep();
        Measure band = measure(goal, "9");

        assertThatThrownBy(() -> service.addResult(step, linked(band.id(), null, "5", LocalDate.of(2026, 10, 1))))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void resultsWithoutAMeasureDoNotTouchTheMeasureRepositories() {
        assertThat(service.addResult(step, result("Quiz", "5", "10", LocalDate.of(2026, 10, 1))).results()).hasSize(1);
    }
}
