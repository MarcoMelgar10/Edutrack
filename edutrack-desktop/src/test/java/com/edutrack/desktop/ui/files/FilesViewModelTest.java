package com.edutrack.desktop.ui.files;

import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.application.attachment.AttachmentView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FilesViewModelTest {

    private final AttachmentService attachments = mock(AttachmentService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final FilesViewModel viewModel = new FilesViewModel(attachments, dataChanges, new Messages());
    private final StepId step = StepId.newId();

    private static AttachmentView file(String name) {
        return new AttachmentView(AttachmentId.newId(), name, "audio/mpeg", 1_500_000, Instant.parse("2026-10-28T10:00:00Z"));
    }

    @Test
    void sizesAreWrittenInTheLargestUnitThatFits() {
        assertThat(FilesViewModel.size(0)).isEqualTo("0 B");
        assertThat(FilesViewModel.size(900)).isEqualTo("900 B");
        assertThat(FilesViewModel.size(1536)).isEqualTo("1.5 KB");
        assertThat(FilesViewModel.size(1_500_000)).isEqualTo("1.4 MB");
        assertThat(FilesViewModel.size(3L * 1024 * 1024 * 1024)).isEqualTo("3.0 GB");
    }

    @Test
    void showingAStepListsItsFilesAndCountsThemInTheTab() {
        when(attachments.forOwner(RecordOwner.ofStep(step))).thenReturn(List.of(file("talk.mp3"), file("notes.pdf")));

        viewModel.show(step);

        assertThat(viewModel.files()).extracting(AttachmentView::name).containsExactly("talk.mp3", "notes.pdf");
        assertThat(viewModel.tabTextProperty().get()).isEqualTo("Files (2)");
    }

    @Test
    void aFileThatCannotBeAddedIsReportedAndTheOthersStillGoIn() {
        Path good = Path.of("good.mp3");
        Path huge = Path.of("huge.iso");
        when(attachments.add(RecordOwner.ofStep(step), huge))
                .thenThrow(new DomainValidationException("too-large", "File must be at most 200.", "File", 200));
        when(attachments.forOwner(RecordOwner.ofStep(step))).thenReturn(List.of());
        viewModel.show(step);

        viewModel.add(List.of(huge, good));

        verify(attachments).add(RecordOwner.ofStep(step), good);
        assertThat(viewModel.errorProperty().get()).startsWith("huge.iso: ").contains("200");
    }

    @Test
    void deletingAFileAnnouncesTheChange() {
        AttachmentId id = AttachmentId.newId();
        when(attachments.forOwner(any())).thenReturn(List.of());
        viewModel.show(step);
        long[] changes = {0};
        dataChanges.subscribe(() -> changes[0]++);

        viewModel.delete(id);

        verify(attachments).delete(id);
        assertThat(changes[0]).isEqualTo(1);
    }

    @Test
    void clearingStopsFollowingTheStep() {
        when(attachments.forOwner(RecordOwner.ofStep(step))).thenReturn(List.of(file("a.pdf")));
        viewModel.show(step);

        viewModel.clear();

        assertThat(viewModel.files()).isEmpty();
    }
}
