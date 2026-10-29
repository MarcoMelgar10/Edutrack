package com.edutrack.domain.attachment;

import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");

    private final AttachmentId id = AttachmentId.newId();
    private final RecordOwner owner = RecordOwner.ofStep(StepId.newId());

    private Attachment attachment(String name, String mime, long size) {
        return Attachment.create(id, owner, name, id + "/" + name, mime, size, NOW);
    }

    @Test
    void keepsWhatWasGiven() {
        Attachment attachment = attachment("certificate.pdf", "application/pdf", 2048);

        assertThat(attachment.id()).isEqualTo(id);
        assertThat(attachment.owner()).isEqualTo(owner);
        assertThat(attachment.originalName()).isEqualTo("certificate.pdf");
        assertThat(attachment.storedPath()).isEqualTo(id + "/certificate.pdf");
        assertThat(attachment.mimeType()).isEqualTo("application/pdf");
        assertThat(attachment.sizeBytes()).isEqualTo(2048);
        assertThat(attachment.addedAt()).isEqualTo(NOW);
    }

    @Test
    void theTypeIsOptional() {
        assertThat(attachment("notes.xyz", " ", 10).mimeType()).isNull();
    }

    @Test
    void aGoalCanOwnAFile() {
        RecordOwner goal = RecordOwner.ofGoal(GoalId.newId());
        Attachment attachment = Attachment.create(id, goal, "a.txt", id + "/a.txt", null, 1, NOW);

        assertThat(attachment.owner().isStep()).isFalse();
    }

    @Test
    void theNameIsRequiredAndLimited() {
        assertThatThrownBy(() -> attachment(" ", null, 10))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("required"));
        assertThatThrownBy(() -> attachment("x".repeat(256), null, 10))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("too-long");
                    assertThat(e.args()).containsExactly("File name", 255);
                });
    }

    @Test
    void emptyFilesAreRejected() {
        assertThatThrownBy(() -> attachment("a.txt", null, 0))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("not-positive"));
    }

    @Test
    void filesAreLimitedTo200Megabytes() {
        assertThat(attachment("big.bin", null, Attachment.MAX_SIZE_BYTES).sizeBytes()).isEqualTo(200L * 1024 * 1024);

        assertThatThrownBy(() -> attachment("big.bin", null, Attachment.MAX_SIZE_BYTES + 1))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> {
                    assertThat(e.code()).isEqualTo("too-large");
                    assertThat(e.args()).containsExactly("File", 200L);
                });
    }

    @Test
    void theOwnerCanChange() {
        Attachment attachment = attachment("a.txt", null, 1);
        RecordOwner other = RecordOwner.ofStep(StepId.newId());

        attachment.moveTo(other);

        assertThat(attachment.owner()).isEqualTo(other);
    }
}
