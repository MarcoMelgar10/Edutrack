package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.ResourceDetails;
import com.edutrack.domain.record.ResourceId;
import com.edutrack.domain.record.ResourceType;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResourceDialogViewModelTest {

    private final LearningRecordService records = mock(LearningRecordService.class);
    private final ResourceDialogViewModel viewModel = new ResourceDialogViewModel(records, new Messages());
    private final RecordOwner owner = RecordOwner.ofStep(StepId.newId());
    private final RecordView refreshed =
            new RecordView(owner, "", null, List.of(), List.of(), List.of(), RecordSummary.EMPTY);

    @Test
    void startsInCreateModeAsAnArticle() {
        viewModel.create(owner);

        assertThat(viewModel.title()).isEqualTo("New resource");
        assertThat(viewModel.saveLabel()).isEqualTo("Add resource");
        assertThat(viewModel.typeProperty().get()).isEqualTo(ResourceType.ARTICLE);
    }

    @Test
    void isInvalidUntilTheTitleHasText() {
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.resourceTitleProperty().set("   ");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.resourceTitleProperty().set("Cambridge Grammar in Use");
        assertThat(viewModel.validProperty().get()).isTrue();
    }

    @Test
    void submitInCreateModeSendsAddResource() {
        when(records.addResource(any(), any())).thenReturn(refreshed);
        viewModel.create(owner);
        viewModel.resourceTitleProperty().set("Cambridge Grammar in Use");
        viewModel.urlProperty().set("https://example.com/book");
        viewModel.typeProperty().set(ResourceType.BOOK);

        RecordView result = viewModel.submit();

        ArgumentCaptor<ResourceDetails> sent = ArgumentCaptor.forClass(ResourceDetails.class);
        verify(records).addResource(org.mockito.ArgumentMatchers.eq(owner), sent.capture());
        assertThat(sent.getValue()).isEqualTo(
                new ResourceDetails("Cambridge Grammar in Use", "https://example.com/book", ResourceType.BOOK));
        assertThat(result).isSameAs(refreshed);
    }

    @Test
    void editPrefillsEveryFieldAndSubmitsAnUpdate() {
        ResourceId id = ResourceId.newId();
        when(records.updateResource(any(), any(), any())).thenReturn(refreshed);
        viewModel.edit(owner, new RecordView.ResourceView(id, "BBC", "https://bbc.co.uk", ResourceType.VIDEO, true));

        assertThat(viewModel.title()).isEqualTo("Edit resource");
        assertThat(viewModel.saveLabel()).isEqualTo("Save changes");
        assertThat(viewModel.resourceTitleProperty().get()).isEqualTo("BBC");
        assertThat(viewModel.urlProperty().get()).isEqualTo("https://bbc.co.uk");
        assertThat(viewModel.typeProperty().get()).isEqualTo(ResourceType.VIDEO);

        viewModel.resourceTitleProperty().set("BBC Learning English");
        viewModel.submit();

        verify(records).updateResource(owner, id,
                new ResourceDetails("BBC Learning English", "https://bbc.co.uk", ResourceType.VIDEO));
    }

    @Test
    void aResourceWithoutALinkEditsAsAnEmptyField() {
        viewModel.edit(owner, new RecordView.ResourceView(ResourceId.newId(), "Printed book", null,
                ResourceType.BOOK, false));

        assertThat(viewModel.urlProperty().get()).isEmpty();
    }

    @Test
    void domainRejectionsReachTheDialog() {
        when(records.addResource(any(), any())).thenThrow(new DomainValidationException("Link must be a web address."));
        viewModel.create(owner);
        viewModel.resourceTitleProperty().set("Book");
        viewModel.errorProperty().set("old");

        assertThatThrownBy(viewModel::submit)
                .isInstanceOf(DomainValidationException.class)
                .hasMessage("Link must be a web address.");
        assertThat(viewModel.errorProperty().get()).isEmpty();
    }
}
