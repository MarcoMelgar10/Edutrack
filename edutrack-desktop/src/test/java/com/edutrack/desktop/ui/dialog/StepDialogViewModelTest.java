package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StepDialogViewModelTest {

    private final GoalService goalService = mock(GoalService.class);
    private final StepDialogViewModel viewModel = new StepDialogViewModel(goalService, new Messages());
    private final GoalId goalId = GoalId.newId();
    private final StepId stepId = StepId.newId();
    private final GoalDetails refreshed = new GoalDetails(goalId, AreaId.newId(), "IELTS 7.0", null, null,
            Progress.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, false, List.of());

    private StepView step(String hours, StepStatus status) {
        return new StepView(stepId, 2, "Grammar review", "Tenses and articles",
                hours == null ? null : new BigDecimal(hours), status, null, RecordSummary.EMPTY);
    }

    @Test
    void startsInCreateModeAsNotStarted() {
        assertThat(viewModel.title()).isEqualTo("New step");
        assertThat(viewModel.saveLabel()).isEqualTo("Add step");
        assertThat(viewModel.statusProperty().get()).isEqualTo(StepStatus.NOT_STARTED);
    }

    @Test
    void isInvalidUntilTheTitleHasText() {
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.stepTitleProperty().set("   ");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.stepTitleProperty().set("Grammar review");
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void blankHoursAreValidAndMeanNotEstimated() {
        assertThat(hoursSentFor("")).isNull();
        assertThat(hoursSentFor("   ")).isNull();
    }

    @Test
    void acceptsACommaOrADotAsDecimalSeparator() {
        assertThat(hoursSentFor("2,5")).isEqualByComparingTo("2.5");
        assertThat(hoursSentFor("2.5")).isEqualByComparingTo("2.5");
        assertThat(hoursSentFor(".5")).isEqualByComparingTo("0.5");
    }

    @Test
    void acceptsWholeHoursWithSurroundingSpaces() {
        assertThat(hoursSentFor(" 10 ")).isEqualByComparingTo("10");
        assertThat(hoursSentFor("0")).isEqualByComparingTo("0");
    }

    @Test
    void textThatIsNotANumberIsInvalid() {
        viewModel.stepTitleProperty().set("Grammar review");

        for (String typed : List.of("abc", "2 h", "1.2.3", "1e3", "--1", "2,5,1")) {
            viewModel.hoursProperty().set(typed);
            assertThat(viewModel.hoursValidProperty().get()).as(typed).isFalse();
            assertThat(viewModel.validProperty().get()).as(typed).isFalse();
        }
    }

    @Test
    void negativeHoursAreInvalid() {
        viewModel.stepTitleProperty().set("Grammar review");
        viewModel.hoursProperty().set("-1");

        assertThat(viewModel.hoursValidProperty().get()).isFalse();
        assertThat(viewModel.validProperty().get()).isFalse();
    }

    @Test
    void validAgainOnceTheHoursAreFixed() {
        viewModel.stepTitleProperty().set("Grammar review");
        viewModel.hoursProperty().set("abc");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.hoursProperty().set("3");

        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void submitRefusesInvalidHoursWithTheHint() {
        viewModel.create(goalId);
        viewModel.stepTitleProperty().set("Grammar review");
        viewModel.hoursProperty().set("abc");

        assertThatThrownBy(viewModel::submit)
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Enter hours as a number, e.g. 2.5");
        verify(goalService, never()).addStep(any());
    }

    @Test
    void editPrefillsEveryFieldAndSwitchesLabels() {
        viewModel.edit(goalId, step("10.0", StepStatus.IN_PROGRESS));

        assertThat(viewModel.title()).isEqualTo("Edit step");
        assertThat(viewModel.saveLabel()).isEqualTo("Save changes");
        assertThat(viewModel.stepTitleProperty().get()).isEqualTo("Grammar review");
        assertThat(viewModel.descriptionProperty().get()).isEqualTo("Tenses and articles");
        assertThat(viewModel.hoursProperty().get()).isEqualTo("10");
        assertThat(viewModel.statusProperty().get()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void editShowsFractionalHoursAndMissingValuesPlainly() {
        viewModel.edit(goalId, step("2.5", StepStatus.NOT_STARTED));
        assertThat(viewModel.hoursProperty().get()).isEqualTo("2.5");

        viewModel.edit(goalId, new StepView(stepId, 1, "Reading", null, null, StepStatus.DONE, null, RecordSummary.EMPTY));
        assertThat(viewModel.hoursProperty().get()).isEmpty();
        assertThat(viewModel.descriptionProperty().get()).isEmpty();
        assertThat(viewModel.statusProperty().get()).isEqualTo(StepStatus.DONE);
    }

    @Test
    void submitInCreateModeSendsAddStep() {
        when(goalService.addStep(any())).thenReturn(refreshed);
        viewModel.create(goalId);
        viewModel.stepTitleProperty().set("Grammar review");
        viewModel.descriptionProperty().set("Tenses");
        viewModel.hoursProperty().set("2,5");
        viewModel.statusProperty().set(StepStatus.IN_PROGRESS);

        GoalDetails result = viewModel.submit();

        ArgumentCaptor<GoalCommands.AddStep> captured = ArgumentCaptor.forClass(GoalCommands.AddStep.class);
        verify(goalService).addStep(captured.capture());
        GoalCommands.AddStep sent = captured.getValue();
        assertThat(sent.goalId()).isEqualTo(goalId);
        assertThat(sent.title()).isEqualTo("Grammar review");
        assertThat(sent.description()).isEqualTo("Tenses");
        assertThat(sent.estimatedHours()).isEqualByComparingTo("2.5");
        assertThat(sent.status()).isEqualTo(StepStatus.IN_PROGRESS);
        assertThat(result).isSameAs(refreshed);
    }

    @Test
    void submitInEditModeSendsUpdateStepWithTheStepId() {
        when(goalService.updateStep(any())).thenReturn(refreshed);
        viewModel.edit(goalId, step("10.0", StepStatus.NOT_STARTED));
        viewModel.stepTitleProperty().set("Grammar review, part 2");
        viewModel.statusProperty().set(StepStatus.DONE);

        GoalDetails result = viewModel.submit();

        ArgumentCaptor<GoalCommands.UpdateStep> sent = ArgumentCaptor.forClass(GoalCommands.UpdateStep.class);
        verify(goalService).updateStep(sent.capture());
        GoalCommands.UpdateStep command = sent.getValue();
        assertThat(command.goalId()).isEqualTo(goalId);
        assertThat(command.stepId()).isEqualTo(stepId);
        assertThat(command.title()).isEqualTo("Grammar review, part 2");
        assertThat(command.description()).isEqualTo("Tenses and articles");
        assertThat(command.estimatedHours()).isEqualByComparingTo("10");
        assertThat(command.status()).isEqualTo(StepStatus.DONE);
        assertThat(result).isSameAs(refreshed);
    }

    @Test
    void submitClearsTheShownError() {
        when(goalService.addStep(any())).thenReturn(refreshed);
        viewModel.create(goalId);
        viewModel.stepTitleProperty().set("Grammar review");
        viewModel.errorProperty().set("Title is required.");

        viewModel.submit();

        assertThat(viewModel.errorProperty().get()).isEmpty();
    }

    @Test
    void domainRejectionsReachTheDialog() {
        when(goalService.addStep(any())).thenThrow(new DomainValidationException("Title is required."));
        viewModel.create(goalId);

        assertThatThrownBy(viewModel::submit)
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Title is required.");
    }

    /** Types the hours into a valid create-mode form, saves, and returns the hours the service received. */
    private BigDecimal hoursSentFor(String typed) {
        when(goalService.addStep(any())).thenReturn(refreshed);
        viewModel.create(goalId);
        viewModel.stepTitleProperty().set("Grammar review");
        viewModel.hoursProperty().set(typed);
        assertThat(viewModel.hoursValidProperty().get()).as("hours \"%s\" valid", typed).isTrue();
        assertThat(viewModel.validProperty().get()).as("form with hours \"%s\" valid", typed).isTrue();

        viewModel.submit();

        ArgumentCaptor<GoalCommands.AddStep> sent = ArgumentCaptor.forClass(GoalCommands.AddStep.class);
        verify(goalService, atLeastOnce()).addStep(sent.capture());
        return sent.getValue().estimatedHours(); // getValue() is the most recent call
    }
}
