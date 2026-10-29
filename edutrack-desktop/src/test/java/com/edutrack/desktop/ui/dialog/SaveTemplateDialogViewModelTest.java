package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.template.GoalTemplate;
import com.edutrack.application.template.TemplateService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SaveTemplateDialogViewModelTest {

    private final TemplateService templateService = mock(TemplateService.class);
    private final SaveTemplateDialogViewModel viewModel =
            new SaveTemplateDialogViewModel(templateService, new Messages());
    private final GoalId goalId = GoalId.newId();

    @Test
    void startsFromTheGoalTitleAndHasItsLabels() {
        viewModel.create(goalId, "IELTS 7.0");

        assertThat(viewModel.nameProperty().get()).isEqualTo("IELTS 7.0");
        assertThat(viewModel.title()).isEqualTo("Save as template");
        assertThat(viewModel.saveLabel()).isEqualTo("Save template");
    }

    @Test
    void isInvalidWithoutAName() {
        viewModel.create(goalId, "IELTS");
        assertThat(viewModel.validProperty().get()).isTrue();

        viewModel.nameProperty().set("  ");

        assertThat(viewModel.validProperty().get()).isFalse();
    }

    @Test
    void submitSavesTheGoalAsATemplateUnderTheName() {
        GoalTemplate saved = new GoalTemplate("my-plan", "My plan", null, null, null, null, null, null, null, null);
        when(templateService.saveAsTemplate(goalId, "My plan")).thenReturn(saved);
        viewModel.create(goalId, "IELTS");
        viewModel.nameProperty().set("My plan");

        assertThat(viewModel.submit()).isSameAs(saved);
        verify(templateService).saveAsTemplate(goalId, "My plan");
    }
}
