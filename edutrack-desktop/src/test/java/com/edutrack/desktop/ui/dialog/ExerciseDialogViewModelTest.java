package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseDialogViewModelTest {

    private final LearningRecordService records = mock(LearningRecordService.class);
    private final ExerciseDialogViewModel viewModel = new ExerciseDialogViewModel(records, new Messages());
    private final RecordOwner owner = RecordOwner.ofStep(StepId.newId());
    private final ExerciseId id = ExerciseId.newId();
    private final RecordView refreshed =
            new RecordView(owner, "", null, List.of(), List.of(), List.of(), RecordSummary.EMPTY);

    private void editExistingExercise() {
        viewModel.edit(owner, new RecordView.ExerciseView(id, "Unit 3", false, null));
    }

    @Test
    void editPrefillsTheTitleAndUsesEditLabels() {
        editExistingExercise();

        assertThat(viewModel.title()).isEqualTo("Edit exercise");
        assertThat(viewModel.saveLabel()).isEqualTo("Save changes");
        assertThat(viewModel.exerciseTitleProperty().get()).isEqualTo("Unit 3");
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void isInvalidWhileTheTitleIsBlank() {
        editExistingExercise();

        viewModel.exerciseTitleProperty().set("  ");

        assertThat(viewModel.validProperty().get()).isFalse();
    }

    @Test
    void submitRenamesTheExercise() {
        when(records.renameExercise(owner, id, "Unit 3, part 2")).thenReturn(refreshed);
        editExistingExercise();
        viewModel.exerciseTitleProperty().set("Unit 3, part 2");

        RecordView result = viewModel.submit();

        verify(records).renameExercise(owner, id, "Unit 3, part 2");
        assertThat(result).isSameAs(refreshed);
    }

    @Test
    void domainRejectionsReachTheDialog() {
        when(records.renameExercise(owner, id, "x")).thenThrow(new DomainValidationException("Title is too long."));
        editExistingExercise();
        viewModel.exerciseTitleProperty().set("x");

        assertThatThrownBy(viewModel::submit).isInstanceOf(DomainValidationException.class)
                .hasMessage("Title is too long.");
    }
}
