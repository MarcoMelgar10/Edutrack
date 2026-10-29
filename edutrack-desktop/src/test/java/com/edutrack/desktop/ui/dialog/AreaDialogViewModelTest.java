package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.area.AreaCommands;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AreaDialogViewModelTest {

    private final AreaService areaService = mock(AreaService.class);
    private final AreaDialogViewModel viewModel = new AreaDialogViewModel(areaService, new Messages());
    private final AreaId areaId = AreaId.newId();

    private AreaSummary summary(String name, String description, AreaIcon icon) {
        return new AreaSummary(areaId, name, description, icon, 2, Progress.ZERO);
    }

    @Test
    void startsInCreateModeWithTheBookIcon() {
        assertThat(viewModel.title()).isEqualTo("New area");
        assertThat(viewModel.saveLabel()).isEqualTo("Create area");
        assertThat(viewModel.iconProperty().get()).isEqualTo(AreaIcon.BOOK);
    }

    @Test
    void isInvalidUntilTheNameHasText() {
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.nameProperty().set("   ");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.nameProperty().set("English");
        assertThat(viewModel.validProperty().get()).isTrue();

        viewModel.nameProperty().set("");
        assertThat(viewModel.validProperty().get()).isFalse();
    }

    @Test
    void editPrefillsTheFormAndSwitchesLabels() {
        viewModel.edit(summary("English", "Grammar and listening", AreaIcon.GLOBE));

        assertThat(viewModel.title()).isEqualTo("Edit area");
        assertThat(viewModel.saveLabel()).isEqualTo("Save changes");
        assertThat(viewModel.nameProperty().get()).isEqualTo("English");
        assertThat(viewModel.descriptionProperty().get()).isEqualTo("Grammar and listening");
        assertThat(viewModel.iconProperty().get()).isEqualTo(AreaIcon.GLOBE);
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void editShowsAMissingDescriptionAsEmptyText() {
        viewModel.edit(summary("English", null, AreaIcon.GLOBE));

        assertThat(viewModel.descriptionProperty().get()).isEmpty();
    }

    @Test
    void submitInCreateModeSendsCreateArea() {
        AreaSummary created = summary("Technologies", "Code", AreaIcon.CODE);
        when(areaService.createArea(any())).thenReturn(created);
        viewModel.nameProperty().set("Technologies");
        viewModel.descriptionProperty().set("Code");
        viewModel.iconProperty().set(AreaIcon.CODE);

        AreaSummary result = viewModel.submit();

        ArgumentCaptor<AreaCommands.CreateArea> sent = ArgumentCaptor.forClass(AreaCommands.CreateArea.class);
        verify(areaService).createArea(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new AreaCommands.CreateArea("Technologies", "Code", AreaIcon.CODE));
        assertThat(result).isSameAs(created);
    }

    @Test
    void submitInEditModeSendsUpdateAreaWithTheAreaId() {
        AreaSummary updated = summary("English B2", "", AreaIcon.GLOBE);
        when(areaService.updateArea(any())).thenReturn(updated);
        viewModel.edit(summary("English", "", AreaIcon.GLOBE));
        viewModel.nameProperty().set("English B2");

        AreaSummary result = viewModel.submit();

        ArgumentCaptor<AreaCommands.UpdateArea> sent = ArgumentCaptor.forClass(AreaCommands.UpdateArea.class);
        verify(areaService).updateArea(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new AreaCommands.UpdateArea(areaId, "English B2", "", AreaIcon.GLOBE));
        assertThat(result).isSameAs(updated);
    }

    @Test
    void submitClearsTheShownError() {
        when(areaService.createArea(any())).thenReturn(summary("English", "", AreaIcon.BOOK));
        viewModel.nameProperty().set("English");
        viewModel.errorProperty().set("An area named \"English\" already exists.");

        viewModel.submit();

        assertThat(viewModel.errorProperty().get()).isEmpty();
    }

    @Test
    void domainRejectionsReachTheDialog() {
        when(areaService.createArea(any())).thenThrow(new DomainValidationException("Name is required."));

        assertThatThrownBy(viewModel::submit)
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Name is required.");
    }
}
