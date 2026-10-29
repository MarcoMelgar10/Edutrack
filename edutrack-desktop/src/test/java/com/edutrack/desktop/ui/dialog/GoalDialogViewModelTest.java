package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.goal.GoalCommands;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoalDialogViewModelTest {

    private final GoalService goalService = mock(GoalService.class);
    private final GoalDialogViewModel viewModel = new GoalDialogViewModel(goalService, new Messages());
    private final AreaId areaId = AreaId.newId();
    private final GoalId goalId = GoalId.newId();

    private GoalDetails details(String title, String description, LocalDate deadline) {
        return new GoalDetails(goalId, areaId, title, description, deadline, Progress.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, 0, false, List.of());
    }

    @Test
    void startsInCreateMode() {
        assertThat(viewModel.title()).isEqualTo("New goal");
        assertThat(viewModel.saveLabel()).isEqualTo("Create goal");
        assertThat(viewModel.deadlineProperty().get()).isNull();
    }

    @Test
    void isInvalidUntilTheTitleHasText() {
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.goalTitleProperty().set("  ");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.goalTitleProperty().set("IELTS 7.0");
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void descriptionAndDeadlineAreOptional() {
        viewModel.goalTitleProperty().set("IELTS 7.0");

        assertThat(viewModel.descriptionProperty().get()).isEmpty();
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void editPrefillsTheFormAndSwitchesLabels() {
        viewModel.edit(details("IELTS 7.0", "Band 7 overall", LocalDate.of(2027, 3, 1)));

        assertThat(viewModel.title()).isEqualTo("Edit goal");
        assertThat(viewModel.saveLabel()).isEqualTo("Save changes");
        assertThat(viewModel.goalTitleProperty().get()).isEqualTo("IELTS 7.0");
        assertThat(viewModel.descriptionProperty().get()).isEqualTo("Band 7 overall");
        assertThat(viewModel.deadlineProperty().get()).isEqualTo(LocalDate.of(2027, 3, 1));
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void editShowsAMissingDescriptionAsEmptyText() {
        viewModel.edit(details("IELTS 7.0", null, null));

        assertThat(viewModel.descriptionProperty().get()).isEmpty();
        assertThat(viewModel.deadlineProperty().get()).isNull();
    }

    @Test
    void submitInCreateModeSendsCreateGoalForTheArea() {
        GoalDetails created = details("IELTS 7.0", "Band 7", LocalDate.of(2027, 3, 1));
        when(goalService.createGoal(any())).thenReturn(created);
        viewModel.create(areaId);
        viewModel.goalTitleProperty().set("IELTS 7.0");
        viewModel.descriptionProperty().set("Band 7");
        viewModel.deadlineProperty().set(LocalDate.of(2027, 3, 1));

        GoalDetails result = viewModel.submit();

        ArgumentCaptor<GoalCommands.CreateGoal> sent = ArgumentCaptor.forClass(GoalCommands.CreateGoal.class);
        verify(goalService).createGoal(sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(new GoalCommands.CreateGoal(areaId, "IELTS 7.0", "Band 7", LocalDate.of(2027, 3, 1)));
        assertThat(result).isSameAs(created);
    }

    @Test
    void submitInEditModeSendsUpdateGoalWithTheGoalId() {
        GoalDetails updated = details("IELTS 7.5", "", null);
        when(goalService.updateGoal(any())).thenReturn(updated);
        viewModel.edit(details("IELTS 7.0", null, LocalDate.of(2027, 3, 1)));
        viewModel.goalTitleProperty().set("IELTS 7.5");
        viewModel.deadlineProperty().set(null);

        GoalDetails result = viewModel.submit();

        ArgumentCaptor<GoalCommands.UpdateGoal> sent = ArgumentCaptor.forClass(GoalCommands.UpdateGoal.class);
        verify(goalService).updateGoal(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new GoalCommands.UpdateGoal(goalId, "IELTS 7.5", "", null));
        assertThat(result).isSameAs(updated);
    }

    @Test
    void submitClearsTheShownError() {
        when(goalService.createGoal(any())).thenReturn(details("IELTS 7.0", "", null));
        viewModel.create(areaId);
        viewModel.goalTitleProperty().set("IELTS 7.0");
        viewModel.errorProperty().set("Deadline must be a date such as 01 Mar 2027.");

        viewModel.submit();

        assertThat(viewModel.errorProperty().get()).isEmpty();
    }

    @Test
    void domainRejectionsReachTheDialog() {
        when(goalService.createGoal(any())).thenThrow(new DomainValidationException("Title is required."));
        viewModel.create(areaId);

        assertThatThrownBy(viewModel::submit)
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Title is required.");
    }
}
