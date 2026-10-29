package com.edutrack.domain.evidence;

import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    private final GoalId goalId = GoalId.newId();

    private static EvidenceDetails details(String title, LocalDate takenOn, String url, String note) {
        return new EvidenceDetails(null, EvidenceKind.PROJECT, title, takenOn, url, note, null);
    }

    private Evidence evidenceWithNote() {
        return Evidence.create(goalId, details("Portfolio site", TODAY, null, "Deployed"), null, TODAY, NOW);
    }

    private static void assertRejected(Runnable action, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    @Test
    void createKeepsNormalizedValues() {
        StepId step = StepId.newId();
        CompetencyId competency = CompetencyId.newId();
        Evidence evidence = Evidence.create(goalId, new EvidenceDetails(step, EvidenceKind.RECORDING, "  Talk ",
                TODAY, "example.com/talk", " Went well ", competency), null, TODAY, NOW);

        assertThat(evidence.goalId()).isEqualTo(goalId);
        assertThat(evidence.stepId()).isEqualTo(step);
        assertThat(evidence.kind()).isEqualTo(EvidenceKind.RECORDING);
        assertThat(evidence.title()).isEqualTo("Talk");
        assertThat(evidence.takenOn()).isEqualTo(TODAY);
        assertThat(evidence.url()).isEqualTo("https://example.com/talk");
        assertThat(evidence.note()).isEqualTo("Went well");
        assertThat(evidence.competencyId()).isEqualTo(competency);
        assertThat(evidence.attachmentId()).isNull();
        assertThat(evidence.createdAt()).isEqualTo(NOW);
    }

    @Test
    void aTitleKindAndDateAreRequired() {
        assertRejected(() -> Evidence.create(goalId, details(" ", TODAY, null, "n"), null, TODAY, NOW), "required");
        assertRejected(() -> Evidence.create(goalId, details("T", null, null, "n"), null, TODAY, NOW), "required");
        assertRejected(() -> Evidence.create(goalId,
                new EvidenceDetails(null, null, "T", TODAY, null, "n", null), null, TODAY, NOW), "required");
    }

    @Test
    void theTitleIsLimitedTo160Characters() {
        assertRejected(() -> Evidence.create(goalId, details("x".repeat(161), TODAY, null, "n"), null, TODAY, NOW),
                "too-long");
    }

    @Test
    void theNoteIsLimitedTo2000Characters() {
        assertRejected(() -> Evidence.create(goalId, details("T", TODAY, null, "x".repeat(2001)), null, TODAY, NOW),
                "too-long");
    }

    @Test
    void evidenceCannotBeDatedInTheFuture() {
        assertRejected(() -> Evidence.create(goalId, details("T", TODAY.plusDays(1), null, "n"), null, TODAY, NOW),
                "future-date");
    }

    @Test
    void theLinkMustBeAWebAddress() {
        assertRejected(() -> Evidence.create(goalId, details("T", TODAY, "ftp://x.com/a", null), null, TODAY, NOW),
                "not-web-address");
    }

    @Test
    void aLinkAFileOrANoteIsNeeded() {
        assertRejected(() -> Evidence.create(goalId, details("T", TODAY, null, null), null, TODAY, NOW), "evidence-empty");
        assertRejected(() -> Evidence.create(goalId, details("T", TODAY, " ", "  "), null, TODAY, NOW), "evidence-empty");

        assertThat(Evidence.create(goalId, details("T", TODAY, "example.com", null), null, TODAY, NOW)).isNotNull();
        assertThat(Evidence.create(goalId, details("T", TODAY, null, null), AttachmentId.newId(), TODAY, NOW)).isNotNull();
        assertThat(Evidence.create(goalId, details("T", TODAY, null, "note"), null, TODAY, NOW)).isNotNull();
    }

    @Test
    void updateChangesEverythingOrNothing() {
        Evidence evidence = evidenceWithNote();

        evidence.update(new EvidenceDetails(null, EvidenceKind.ESSAY, "Essay", TODAY.minusDays(3), "https://a.org",
                null, null), TODAY);

        assertThat(evidence.kind()).isEqualTo(EvidenceKind.ESSAY);
        assertThat(evidence.title()).isEqualTo("Essay");
        assertThat(evidence.note()).isNull();

        assertRejected(() -> evidence.update(details("Other", TODAY.plusDays(1), "https://b.org", "n"), TODAY), "future-date");
        assertRejected(() -> evidence.update(details(" ", TODAY, "https://b.org", "n"), TODAY), "required");
        assertThat(evidence.title()).isEqualTo("Essay");
        assertThat(evidence.url()).isEqualTo("https://a.org");
    }

    @Test
    void updateCannotLeaveTheEvidenceEmpty() {
        Evidence evidence = evidenceWithNote();

        assertRejected(() -> evidence.update(details("Portfolio site", TODAY, null, null), TODAY), "evidence-empty");
        assertThat(evidence.note()).isEqualTo("Deployed");
    }

    @Test
    void anAttachmentKeepsEvidenceValidWithoutLinkOrNote() {
        AttachmentId file = AttachmentId.newId();
        Evidence evidence = Evidence.create(goalId, details("Certificate", TODAY, null, null), file, TODAY, NOW);

        evidence.update(details("Certificate B2", TODAY, null, null), TODAY);

        assertThat(evidence.attachmentId()).isEqualTo(file);
        assertThat(evidence.title()).isEqualTo("Certificate B2");
    }

    @Test
    void attachReplacesTheFileAndDetachRemovesIt() {
        Evidence evidence = evidenceWithNote();
        AttachmentId first = AttachmentId.newId();
        AttachmentId second = AttachmentId.newId();

        evidence.attach(first);
        evidence.attach(second);
        assertThat(evidence.attachmentId()).isEqualTo(second);

        evidence.detach();
        assertThat(evidence.attachmentId()).isNull();
    }

    @Test
    void detachIsRefusedWhenTheFileWasTheOnlyContent() {
        AttachmentId file = AttachmentId.newId();
        Evidence evidence = Evidence.create(goalId, details("Certificate", TODAY, null, null), file, TODAY, NOW);

        assertRejected(evidence::detach, "evidence-empty");
        assertThat(evidence.attachmentId()).isEqualTo(file);
    }

    @Test
    void restoreSkipsTheDateCheck() {
        Evidence evidence = Evidence.restore(EvidenceId.newId(), goalId, null, EvidenceKind.OTHER, "Old",
                LocalDate.of(2099, 1, 1), null, null, null, "n", NOW);

        assertThat(evidence.takenOn()).isEqualTo(LocalDate.of(2099, 1, 1));
    }

    @Test
    void restoreAcceptsEvidenceThatLostItsFile() {
        Evidence evidence = Evidence.restore(EvidenceId.newId(), goalId, null, EvidenceKind.CERTIFICATE, "Certificate",
                TODAY, null, null, null, null, NOW);

        assertThat(evidence.attachmentId()).isNull();
        assertThat(evidence.title()).isEqualTo("Certificate");
    }
}
