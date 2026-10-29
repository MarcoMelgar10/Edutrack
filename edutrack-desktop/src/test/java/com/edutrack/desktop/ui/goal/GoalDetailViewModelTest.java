package com.edutrack.desktop.ui.goal;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalDetailViewModelTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);
    private final GoalService goalService = mock(GoalService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final LearningRecordService recordService = mock(LearningRecordService.class);
    private final GoalDetailViewModel viewModel = new GoalDetailViewModel(goalService, recordService, dataChanges, clock, new Messages());

    {
        // a goal without any saved learning opens as an empty record
        when(recordService.open(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> record(call.getArgument(0)));
    }

    private final AtomicInteger changes = new AtomicInteger();
    private final GoalId goalId = GoalId.newId();
    private final AreaId areaId = AreaId.newId();
    private final StepId grammar = StepId.newId();
    private final StepId listening = StepId.newId();

    private StepView step(StepId id, int number, String title, String hours, StepStatus status, Instant completedAt) {
        return new StepView(id, number, title, null, new BigDecimal(hours), status, completedAt, RecordSummary.EMPTY);
    }

    /** "IELTS 7.0": 10 of 38 hours done, so 26.32%. */
    private GoalDetails ieltsInProgress() {
        return new GoalDetails(goalId, areaId, "IELTS 7.0", "Band 7 overall", LocalDate.of(2027, 3, 1),
                new Progress(new BigDecimal("26.32")), new BigDecimal("10.0"), new BigDecimal("38.0"), 0, false,
                List.of(step(grammar, 1, "Grammar review", "10.0", StepStatus.DONE, Instant.parse("2026-10-20T08:00:00Z")),
                        step(listening, 2, "Listening practice", "28.0", StepStatus.NOT_STARTED, null)));
    }

    private GoalDetails ieltsCompleted() {
        return new GoalDetails(goalId, areaId, "IELTS 7.0", null, LocalDate.of(2027, 3, 1), Progress.COMPLETE,
                new BigDecimal("38.0"), new BigDecimal("38.0"), 0, true,
                List.of(step(grammar, 1, "Grammar review", "10.0", StepStatus.DONE, Instant.parse("2026-10-25T08:00:00Z")),
                        step(listening, 2, "Listening practice", "28.0", StepStatus.DONE, Instant.parse("2026-10-27T09:30:00Z"))));
    }

    private RecordView record(RecordOwner owner, RecordView.ResultView... results) {
        return new RecordView(owner, "", null, List.of(), List.of(), List.of(results), RecordSummary.EMPTY);
    }

    private RecordView.ResultView result(String title) {
        return new RecordView.ResultView(AssessmentId.newId(), title, new BigDecimal("6.5"), new BigDecimal("9"),
                LocalDate.of(2026, 10, 12), null, new Progress(new BigDecimal("72.22")));
    }

    private void showIelts() {
        when(recordService.open(RecordOwner.ofGoal(goalId))).thenReturn(record(RecordOwner.ofGoal(goalId), result("Mock test")));
        when(goalService.getGoal(goalId)).thenReturn(ieltsInProgress());
        viewModel.show(goalId);
    }

    @Test
    void startsWithoutAGoal() {
        assertThat(viewModel.hasGoalProperty().get()).isFalse();
        assertThat(viewModel.steps()).isEmpty();
        assertThat(viewModel.completedProperty().get()).isFalse();
    }

    @Test
    void showRendersTheGoal() {
        showIelts();

        assertThat(viewModel.hasGoalProperty().get()).isTrue();
        assertThat(viewModel.titleProperty().get()).isEqualTo("IELTS 7.0");
        assertThat(viewModel.descriptionProperty().get()).isEqualTo("Band 7 overall");
        assertThat(viewModel.deadlineTextProperty().get()).isEqualTo("Deadline 01 Mar 2027 · 125 days left");
        assertThat(viewModel.progressTextProperty().get()).isEqualTo("26%");
        assertThat(viewModel.effortTextProperty().get()).isEqualTo("10 / 38 h");
        assertThat(viewModel.progressProperty().get()).isEqualTo(0.2632, within(1e-9));
        assertThat(viewModel.steps()).extracting(StepView::title).containsExactly("Grammar review", "Listening practice");
        assertThat(viewModel.hasStepsProperty().get()).isTrue();
        assertThat(viewModel.completedProperty().get()).isFalse();
    }

    @Test
    void aMissingDescriptionShowsAsEmptyText() {
        when(goalService.getGoal(goalId)).thenReturn(ieltsCompleted());

        viewModel.show(goalId);

        assertThat(viewModel.descriptionProperty().get()).isEmpty();
    }

    @Test
    void aGoalWithoutStepsHasNoSteps() {
        GoalDetails empty = new GoalDetails(goalId, areaId, "IELTS 7.0", null, null, Progress.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, false, List.of());
        when(goalService.getGoal(goalId)).thenReturn(empty);

        viewModel.show(goalId);

        assertThat(viewModel.hasGoalProperty().get()).isTrue();
        assertThat(viewModel.hasStepsProperty().get()).isFalse();
        assertThat(viewModel.deadlineTextProperty().get()).isEqualTo("No deadline");
        assertThat(viewModel.effortTextProperty().get()).isEqualTo("0 / 0 h");
    }

    @Test
    void completionIsDatedByTheLatestFinishedStep() {
        when(goalService.getGoal(goalId)).thenReturn(ieltsCompleted());

        viewModel.show(goalId);

        assertThat(viewModel.completedProperty().get()).isTrue();
        assertThat(viewModel.completionTextProperty().get()).isEqualTo("Goal completed · 27 Oct 2026");
        assertThat(viewModel.progressTextProperty().get()).isEqualTo("100%");
    }

    @Test
    void completionWithoutAnyDateStaysUndated() {
        GoalDetails undated = new GoalDetails(goalId, areaId, "IELTS 7.0", null, null, Progress.COMPLETE,
                BigDecimal.TEN, BigDecimal.TEN, 0, true,
                List.of(step(grammar, 1, "Grammar review", "10.0", StepStatus.DONE, null)));
        when(goalService.getGoal(goalId)).thenReturn(undated);

        viewModel.show(goalId);

        assertThat(viewModel.completionTextProperty().get()).isEqualTo("Goal completed");
    }

    @Test
    void cycleStatusCallsTheServiceRendersTheResultAndSignalsTheChange() {
        showIelts();
        dataChanges.subscribe(changes::incrementAndGet);
        when(goalService.cycleStepStatus(goalId, listening)).thenReturn(ieltsCompleted());

        viewModel.cycleStatus(listening);

        verify(goalService).cycleStepStatus(goalId, listening);
        assertThat(viewModel.completedProperty().get()).isTrue();
        assertThat(viewModel.progressTextProperty().get()).isEqualTo("100%");
        assertThat(changes).hasValue(1);
    }

    @Test
    void moveStepAndRemoveStepGoThroughTheService() {
        showIelts();
        dataChanges.subscribe(changes::incrementAndGet);
        when(goalService.moveStep(goalId, listening, MoveDirection.UP)).thenReturn(ieltsInProgress());
        when(goalService.removeStep(goalId, grammar)).thenReturn(ieltsInProgress());

        viewModel.moveStep(listening, MoveDirection.UP);
        viewModel.removeStep(grammar);

        verify(goalService).moveStep(goalId, listening, MoveDirection.UP);
        verify(goalService).removeStep(goalId, grammar);
        assertThat(changes).hasValue(2);
    }

    @Test
    void applyShowsADialogResult() {
        showIelts();

        viewModel.apply(ieltsCompleted());

        assertThat(viewModel.completedProperty().get()).isTrue();
        assertThat(viewModel.steps()).hasSize(2);
    }

    @Test
    void deleteGoalDeletesItClearsThePanelAndSignalsTheChange() {
        showIelts();
        dataChanges.subscribe(changes::incrementAndGet);

        viewModel.deleteGoal();

        verify(goalService).deleteGoal(goalId);
        assertThat(viewModel.hasGoalProperty().get()).isFalse();
        assertThat(viewModel.steps()).isEmpty();
        assertThat(changes).hasValue(1);
    }

    @Test
    void showNullClearsThePanel() {
        showIelts();

        viewModel.show(null);

        assertThat(viewModel.hasGoalProperty().get()).isFalse();
        assertThat(viewModel.goalProperty().get()).isNull();
        assertThat(viewModel.steps()).isEmpty();
        assertThat(viewModel.completedProperty().get()).isFalse();
    }

    @Test
    void showingTheSameGoalAgainDoesNotReloadIt() {
        showIelts();

        viewModel.show(goalId);
        viewModel.show(goalId);

        verify(goalService, times(1)).getGoal(goalId);
    }

    @Test
    void goalResultsLoadOnceAndAreExposedWithTheOwner() {
        showIelts();

        when(goalService.cycleStepStatus(goalId, listening)).thenReturn(ieltsInProgress());
        viewModel.apply(ieltsInProgress());
        viewModel.cycleStatus(listening);

        assertThat(viewModel.results()).extracting(RecordView.ResultView::title).containsExactly("Mock test");
        assertThat(viewModel.resultsOwner()).isEqualTo(RecordOwner.ofGoal(goalId));
        verify(recordService, times(1)).open(RecordOwner.ofGoal(goalId));
    }

    @Test
    void applyResultsAndRemoveResultReplaceTheList() {
        showIelts();
        RecordView.ResultView exam = result("Final exam");
        when(recordService.removeResult(RecordOwner.ofGoal(goalId), exam.id()))
                .thenReturn(record(RecordOwner.ofGoal(goalId)));

        viewModel.applyResults(record(RecordOwner.ofGoal(goalId), exam));
        assertThat(viewModel.results()).extracting(RecordView.ResultView::title).containsExactly("Final exam");

        viewModel.removeResult(exam.id());
        assertThat(viewModel.results()).isEmpty();
    }

    @Test
    void reloadFetchesTheGoalAgainWithoutReloadingResults() {
        showIelts();
        when(goalService.getGoal(goalId)).thenReturn(ieltsCompleted());

        viewModel.reload();

        assertThat(viewModel.completedProperty().get()).isTrue();
        verify(goalService, times(2)).getGoal(goalId);
        verify(recordService, times(1)).open(RecordOwner.ofGoal(goalId));
    }

    @Test
    void reloadWithoutAGoalDoesNothing() {
        viewModel.reload();

        assertThat(viewModel.hasGoalProperty().get()).isFalse();
    }

    @Test
    void clearingThePanelClearsTheResultsAndLoadsThemAgainNextTime() {
        showIelts();

        viewModel.show(null);
        assertThat(viewModel.results()).isEmpty();
        viewModel.show(goalId);

        assertThat(viewModel.results()).hasSize(1);
        verify(recordService, times(2)).open(RecordOwner.ofGoal(goalId));
    }

    @Test
    void aGoalDeletedMeanwhileClearsThePanel() {
        showIelts();
        GoalId other = GoalId.newId();
        when(goalService.getGoal(other)).thenThrow(new NotFoundException("Goal", other));

        viewModel.show(other);

        assertThat(viewModel.hasGoalProperty().get()).isFalse();
    }
}
