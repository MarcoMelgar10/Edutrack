package com.edutrack.application.attachment;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryAttachmentRepository;
import com.edutrack.application.support.InMemoryAttachmentStorage;
import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    @TempDir
    Path tmp;

    private final InMemoryAttachmentRepository repository = new InMemoryAttachmentRepository();
    private final InMemoryAttachmentStorage storage = new InMemoryAttachmentStorage();
    private final AttachmentService service =
            new AttachmentService(repository, storage, Clock.fixed(NOW, ZoneOffset.UTC));
    private final RecordOwner step = RecordOwner.ofStep(StepId.newId());

    private Path file(String name, String content) throws IOException {
        return Files.writeString(tmp.resolve(name), content);
    }

    @Test
    void addCopiesTheFileAndRecordsIt() throws IOException {
        AttachmentView view = service.add(step, file("certificate.pdf", "hello"));

        assertThat(view.name()).isEqualTo("certificate.pdf");
        assertThat(view.sizeBytes()).isEqualTo(5);
        assertThat(view.mimeType()).isEqualTo("text/plain");
        assertThat(view.addedAt()).isEqualTo(NOW);
        assertThat(storage.contains(view.id() + "/certificate.pdf")).isTrue();
        assertThat(service.forOwner(step)).containsExactly(view);
        assertThat(service.pathOf(view.id())).isEqualTo(Path.of("files-root", view.id() + "/certificate.pdf"));
    }

    @Test
    void filesBelongToTheirOwnerOnly() throws IOException {
        RecordOwner goal = RecordOwner.ofGoal(GoalId.newId());
        service.add(step, file("a.txt", "a"));

        assertThat(service.forOwner(goal)).isEmpty();
        assertThat(service.forOwner(step)).hasSize(1);
    }

    @Test
    void forOwnerListsNewestFirst() throws IOException {
        AttachmentService later = new AttachmentService(repository, storage,
                Clock.fixed(NOW.plusSeconds(60), ZoneOffset.UTC));
        AttachmentView first = service.add(step, file("first.txt", "1"));
        AttachmentView second = later.add(step, file("second.txt", "2"));

        assertThat(service.forOwner(step)).extracting(AttachmentView::id).containsExactly(second.id(), first.id());
    }

    @Test
    void anEmptyFileIsRejectedBeforeCopying() throws IOException {
        assertThatThrownBy(() -> service.add(step, file("empty.txt", "")))
                .isInstanceOfSatisfying(DomainValidationException.class,
                        e -> assertThat(e.code()).isEqualTo("not-positive"));
        assertThat(storage.count()).isZero();
        assertThat(repository.size()).isZero();
    }

    @Test
    void aTooLargeFileIsRejectedBeforeCopying() throws IOException {
        Path big = tmp.resolve("big.bin");
        try (RandomAccessFile raf = new RandomAccessFile(big.toFile(), "rw")) {
            raf.setLength(Attachment.MAX_SIZE_BYTES + 1);
        }

        assertThatThrownBy(() -> service.add(step, big))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("too-large");
                    assertThat(e.args()).containsExactly("File", 200L);
                });
        assertThat(storage.count()).isZero();
        assertThat(repository.size()).isZero();
    }

    @Test
    void theCopyIsRemovedWhenSavingTheRowFails() throws IOException {
        repository.failOnSave(true);

        assertThatThrownBy(() -> service.add(step, file("a.txt", "a"))).isInstanceOf(IllegalStateException.class);

        assertThat(storage.count()).isZero();
    }

    @Test
    void deleteRemovesTheRowAndTheFile() throws IOException {
        AttachmentView view = service.add(step, file("a.txt", "a"));

        service.delete(view.id());

        assertThat(service.forOwner(step)).isEmpty();
        assertThat(storage.count()).isZero();
    }

    @Test
    void unknownAttachmentsAreNotFound() {
        assertThatThrownBy(() -> service.pathOf(com.edutrack.domain.attachment.AttachmentId.newId()))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(com.edutrack.domain.attachment.AttachmentId.newId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void moveToHandsTheFileToAnotherOwner() throws IOException {
        RecordOwner goal = RecordOwner.ofGoal(GoalId.newId());
        AttachmentView view = service.add(step, file("a.txt", "a"));

        service.moveTo(view.id(), goal);

        assertThat(service.forOwner(step)).isEmpty();
        assertThat(service.forOwner(goal)).extracting(AttachmentView::id).containsExactly(view.id());
    }

    @Test
    void removeOrphansDeletesOnlyFilesWithoutARow() throws IOException {
        AttachmentView kept = service.add(step, file("kept.txt", "k"));
        storage.plantOrphan("00000000-0000-0000-0000-000000000001/lost.txt");
        storage.plantOrphan("00000000-0000-0000-0000-000000000002/lost.txt");

        int removed = service.removeOrphans();

        assertThat(removed).isEqualTo(2);
        assertThat(List.copyOf(storage.listStored())).containsExactly(kept.id() + "/kept.txt");
        assertThat(service.removeOrphans()).isZero();
    }
}
