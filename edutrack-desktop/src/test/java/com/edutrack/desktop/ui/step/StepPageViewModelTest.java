package com.edutrack.desktop.ui.step;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.PendingWork;
import com.edutrack.desktop.ui.step.StepPageViewModel.SaveState;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.ResourceId;
import com.edutrack.domain.record.ResourceType;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StepPageViewModelTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T14:32:00Z"), ZoneOffset.UTC);
    private final LearningRecordService records = mock(LearningRecordService.class);
    private final GoalService goals = mock(GoalService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final PendingWork pendingWork = new PendingWork();
    private final StepPageViewModel viewModel = new StepPageViewModel(records, goals, dataChanges, pendingWork, clock, new Messages());

    private final AtomicInteger changes = new AtomicInteger();
    private final GoalId goalId = GoalId.newId();
    private final StepId stepId = StepId.newId();
    private final RecordOwner owner = RecordOwner.ofStep(stepId);

    private StepView step(StepStatus status) {
        return new StepView(stepId, 2, "Listening practice", null, new BigDecimal("28.0"), status, null,
                RecordSummary.EMPTY);
    }

    private GoalDetails goal(StepStatus status) {
        return new GoalDetails(goalId, AreaId.newId(), "IELTS 7.0", null, null, Progress.ZERO, BigDecimal.ZERO,
                new BigDecimal("28.0"), 0, false, List.of(step(status)));
    }

    private RecordView.ResourceView resource(String title, boolean reviewed) {
        return new RecordView.ResourceView(ResourceId.newId(), title, null, ResourceType.BOOK, reviewed);
    }

    private RecordView.ExerciseView exercise(String title, boolean done) {
        return new RecordView.ExerciseView(ExerciseId.newId(), title, done, null);
    }

    private RecordView record(String notes, List<RecordView.ResourceView> resources,
                              List<RecordView.ExerciseView> exercises, List<RecordView.ResultView> results) {
        long done = exercises.stream().filter(RecordView.ExerciseView::done).count();
        return new RecordView(owner, notes, Instant.parse("2026-10-26T09:00:00Z"), resources, exercises, results,
                new RecordSummary(!notes.isBlank(), resources.size(), (int) done, exercises.size(), results.size()));
    }

    private RecordView emptyRecord() {
        return new RecordView(owner, "", null, List.of(), List.of(), List.of(), RecordSummary.EMPTY);
    }

    private void openWith(RecordView record) {
        when(records.open(owner)).thenReturn(record);
        viewModel.open(goal(StepStatus.IN_PROGRESS), stepId);
    }

    @Test
    void openRendersHeaderNotesListsAndTabTexts() {
        RecordView.ResultView result = new RecordView.ResultView(AssessmentId.newId(), "Mock", BigDecimal.ONE, null,
                LocalDate.of(2026, 10, 12), null, null);
        openWith(record("# Notes", List.of(resource("Book", false), resource("Course", true)),
                List.of(exercise("A", true), exercise("B", false), exercise("C", false)), List.of(result)));

        assertThat(viewModel.openProperty().get()).isTrue();
        assertThat(viewModel.goalTitleProperty().get()).isEqualTo("IELTS 7.0");
        assertThat(viewModel.stepTitleProperty().get()).isEqualTo("2. Listening practice");
        assertThat(viewModel.statusProperty().get()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(viewModel.statusTextProperty().get()).isEqualTo("In progress");
        assertThat(viewModel.hoursTextProperty().get()).isEqualTo("28h");
        assertThat(viewModel.notesProperty().get()).isEqualTo("# Notes");
        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.SAVED);
        assertThat(viewModel.resources()).hasSize(2);
        assertThat(viewModel.exercises()).hasSize(3);
        assertThat(viewModel.results()).hasSize(1);
        assertThat(viewModel.resourcesTabTextProperty().get()).isEqualTo("Resources (2)");
        assertThat(viewModel.exercisesTabTextProperty().get()).isEqualTo("Exercises 1/3");
        assertThat(viewModel.resultsTabTextProperty().get()).isEqualTo("Results (1)");
        assertThat(viewModel.owner()).isEqualTo(owner);
    }

    @Test
    void anEmptyRecordKeepsThePlainTabNames() {
        openWith(emptyRecord());

        assertThat(viewModel.resourcesTabTextProperty().get()).isEqualTo("Resources");
        assertThat(viewModel.exercisesTabTextProperty().get()).isEqualTo("Exercises");
        assertThat(viewModel.resultsTabTextProperty().get()).isEqualTo("Results");
        assertThat(viewModel.saveTextProperty().get()).isEmpty();
    }

    @Test
    void editingTheNotesMarksThemUnsaved() {
        openWith(emptyRecord());

        viewModel.notesProperty().set("Some text");

        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.UNSAVED);
        assertThat(viewModel.saveTextProperty().get()).isEqualTo("Unsaved changes");
    }

    @Test
    void openingDoesNotMarkTheNotesUnsaved() {
        openWith(record("Existing", List.of(), List.of(), List.of()));

        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.SAVED);
        verify(records, never()).saveNotes(any(), anyString());
    }

    @Test
    void shutdownSavesNotesTypedBeforeTheAutosavePause() {
        openWith(emptyRecord());
        when(records.saveNotes(owner, "Last words")).thenReturn(record("Last words", List.of(), List.of(), List.of()));
        viewModel.notesProperty().set("Last words");

        pendingWork.flushAll();

        verify(records).saveNotes(owner, "Last words");
        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.SAVED);
    }

    @Test
    void saveNotesIfChangedSavesOnceThenDoesNothing() {
        openWith(emptyRecord());
        when(records.saveNotes(owner, "Hello")).thenReturn(record("Hello", List.of(), List.of(), List.of()));
        viewModel.notesProperty().set("Hello");

        viewModel.saveNotesIfChanged();
        viewModel.saveNotesIfChanged();

        verify(records, times(1)).saveNotes(owner, "Hello");
        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.SAVED);
        assertThat(viewModel.saveTextProperty().get()).isEqualTo("Saved · 14:32");
    }

    @Test
    void aRejectedSaveShowsTheReason() {
        openWith(emptyRecord());
        when(records.saveNotes(eq(owner), anyString())).thenThrow(new DomainValidationException("Notes are too long."));
        viewModel.notesProperty().set("Hello");

        viewModel.saveNotesIfChanged();

        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.FAILED);
        assertThat(viewModel.saveTextProperty().get()).isEqualTo("Notes are too long.");
        assertThat(viewModel.notesProperty().get()).isEqualTo("Hello");
    }

    @Test
    void aFailedSaveIsRetriedWhileTheTextStaysChanged() {
        openWith(emptyRecord());
        when(records.saveNotes(eq(owner), anyString()))
                .thenThrow(new DomainValidationException("Notes are too long."))
                .thenReturn(record("Hello", List.of(), List.of(), List.of()));
        viewModel.notesProperty().set("Hello");

        viewModel.saveNotesIfChanged();
        viewModel.saveNotesIfChanged();

        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.SAVED);
    }

    @Test
    void textTypedDuringASaveIsKeptAndStaysUnsaved() {
        openWith(emptyRecord());
        when(records.saveNotes(eq(owner), anyString())).thenAnswer(call -> {
            viewModel.notesProperty().set("Hello world"); // the user keeps typing while the save runs
            return record("Hello", List.of(), List.of(), List.of());
        });
        viewModel.notesProperty().set("Hello");

        viewModel.saveNotesIfChanged();

        assertThat(viewModel.notesProperty().get()).isEqualTo("Hello world");
        assertThat(viewModel.saveStateProperty().get()).isEqualTo(SaveState.UNSAVED);
        assertThat(viewModel.saveTextProperty().get()).isEqualTo("Unsaved changes");
    }

    @Test
    void savingUpdatesTheTabTextsButNotTheListsSelectionOrNotes() {
        openWith(emptyRecord());
        when(records.saveNotes(owner, "Hi")).thenReturn(record("Hi", List.of(resource("Book", false)), List.of(), List.of()));
        viewModel.notesProperty().set("Hi");

        viewModel.saveNotesIfChanged();

        assertThat(viewModel.resourcesTabTextProperty().get()).isEqualTo("Resources (1)");
        assertThat(viewModel.notesProperty().get()).isEqualTo("Hi");
    }

    @Test
    void addingABlankExerciseDoesNothing() {
        openWith(emptyRecord());

        assertThat(viewModel.addExercise("  ")).isFalse();
        assertThat(viewModel.addExercise(null)).isFalse();

        verify(records, never()).addExercise(any(), anyString());
    }

    @Test
    void addingAnExerciseShowsItAndClearsTheError() {
        openWith(emptyRecord());
        when(records.addExercise(owner, "Unit 3")).thenReturn(
                record("", List.of(), List.of(exercise("Unit 3", false)), List.of()));

        assertThat(viewModel.addExercise("Unit 3")).isTrue();

        assertThat(viewModel.exercises()).extracting(RecordView.ExerciseView::title).containsExactly("Unit 3");
        assertThat(viewModel.exercisesTabTextProperty().get()).isEqualTo("Exercises 0/1");
        assertThat(viewModel.exerciseErrorProperty().get()).isEmpty();
    }

    @Test
    void aRejectedExerciseSetsTheError() {
        openWith(emptyRecord());
        when(records.addExercise(eq(owner), anyString())).thenThrow(new DomainValidationException("Title is too long."));

        assertThat(viewModel.addExercise("x".repeat(500))).isFalse();

        assertThat(viewModel.exerciseErrorProperty().get()).isEqualTo("Title is too long.");
    }

    @Test
    void cycleStatusUpdatesTheHeaderAndSignalsTheChange() {
        openWith(emptyRecord());
        dataChanges.subscribe(changes::incrementAndGet);
        when(goals.cycleStepStatus(goalId, stepId)).thenReturn(goal(StepStatus.DONE));

        viewModel.cycleStatus();

        assertThat(viewModel.statusProperty().get()).isEqualTo(StepStatus.DONE);
        assertThat(viewModel.statusTextProperty().get()).isEqualTo("Done");
        assertThat(changes).hasValue(1);
    }

    @Test
    void recordChangesDoNotSignalDataChanges() {
        openWith(emptyRecord());
        dataChanges.subscribe(changes::incrementAndGet);
        ResourceId resourceId = ResourceId.newId();
        ExerciseId exerciseId = ExerciseId.newId();
        when(records.markResourceReviewed(owner, resourceId, true)).thenReturn(emptyRecord());
        when(records.moveExercise(owner, exerciseId, MoveDirection.UP)).thenReturn(emptyRecord());

        viewModel.markResourceReviewed(resourceId, true);
        viewModel.moveExercise(exerciseId, MoveDirection.UP);

        assertThat(changes).hasValue(0);
        verify(records).markResourceReviewed(owner, resourceId, true);
        verify(records).moveExercise(owner, exerciseId, MoveDirection.UP);
    }

    @Test
    void closeSavesPendingNotesAndHidesThePage() {
        openWith(emptyRecord());
        when(records.saveNotes(owner, "Unsaved")).thenReturn(record("Unsaved", List.of(), List.of(), List.of()));
        viewModel.notesProperty().set("Unsaved");

        assertThat(viewModel.close()).isTrue();

        verify(records).saveNotes(owner, "Unsaved");
        assertThat(viewModel.openProperty().get()).isFalse();
    }

    @Test
    void closeKeepsThePageOpenWhenTheNotesCannotBeSaved() {
        openWith(emptyRecord());
        when(records.saveNotes(owner, "Too long"))
                .thenThrow(new DomainValidationException("Notes must be at most 200000 characters."));
        viewModel.notesProperty().set("Too long");

        assertThat(viewModel.close()).isFalse();

        assertThat(viewModel.openProperty().get()).isTrue();
        assertThat(viewModel.notesProperty().get()).isEqualTo("Too long");
        assertThat(viewModel.saveTextProperty().get()).contains("at most 200000");
    }

    @Test
    void closeWithoutChangesDoesNotSave() {
        openWith(emptyRecord());

        viewModel.close();

        verify(records, never()).saveNotes(any(), anyString());
        assertThat(viewModel.openProperty().get()).isFalse();
    }

    @Test
    void nothingIsSavedBeforeAStepIsOpened() {
        viewModel.notesProperty().set("Stray");

        viewModel.saveNotesIfChanged();

        verifyNoInteractions(records);
    }
}
