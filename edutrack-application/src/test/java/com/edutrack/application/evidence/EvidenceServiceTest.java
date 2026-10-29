package com.edutrack.application.evidence;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.application.attachment.AttachmentView;
import com.edutrack.application.support.InMemoryAttachmentRepository;
import com.edutrack.application.support.InMemoryAttachmentStorage;
import com.edutrack.application.support.InMemoryEvidenceRepository;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.evidence.EvidenceDetails;
import com.edutrack.domain.evidence.EvidenceId;
import com.edutrack.domain.evidence.EvidenceKind;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-27T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);

    @TempDir
    Path tmp;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryEvidenceRepository evidence = new InMemoryEvidenceRepository();
    private final InMemoryAttachmentRepository attachmentRows = new InMemoryAttachmentRepository();
    private final InMemoryAttachmentStorage storage = new InMemoryAttachmentStorage();
    private final AttachmentService attachments = new AttachmentService(attachmentRows, storage, clock);
    private final EvidenceService service = new EvidenceService(evidence, attachments, goals, clock);

    private final Goal goal = goalWithSteps();
    private final StepId step = goal.steps().getFirst().id();
    private final StepId otherStep = goal.steps().getLast().id();

    private Goal goalWithSteps() {
        Goal created = Goal.create(AreaId.newId(), "Reach B2", null, null, 0, NOW);
        created.addStep(new StepDetails("Grammar", null, null), StepStatus.NOT_STARTED, NOW);
        created.addStep(new StepDetails("Speaking", null, null), StepStatus.NOT_STARTED, NOW);
        return goals.save(created);
    }

    private static EvidenceDetails details(StepId stepId, String title, LocalDate takenOn, String url, String note) {
        return new EvidenceDetails(stepId, EvidenceKind.PROJECT, title, takenOn, url, note, null);
    }

    private Path file(String name) throws IOException {
        return Files.writeString(tmp.resolve(name), "content");
    }

    @Test
    void addWithANoteNeedsNoFile() {
        EvidenceView view = service.add(goal.id(), details(null, "Portfolio", TODAY, null, "Deployed"), null);

        assertThat(view.title()).isEqualTo("Portfolio");
        assertThat(view.stepId()).isNull();
        assertThat(view.stepTitle()).isNull();
        assertThat(view.attachment()).isNull();
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(service.forGoal(goal.id())).containsExactly(view);
        assertThat(storage.count()).isZero();
    }

    @Test
    void addWithAFileStoresItAsAnAttachmentOfTheStep() throws IOException {
        EvidenceView view = service.add(goal.id(), details(step, "Certificate", TODAY, null, null), file("cert.pdf"));

        assertThat(view.stepTitle()).isEqualTo("Grammar");
        assertThat(view.attachment()).isNotNull();
        assertThat(view.attachment().name()).isEqualTo("cert.pdf");
        assertThat(attachments.forOwner(RecordOwner.ofStep(step))).extracting(AttachmentView::id)
                .containsExactly(view.attachment().id());
        assertThat(attachments.forOwner(RecordOwner.ofGoal(goal.id()))).isEmpty();
    }

    @Test
    void aFileForEvidenceWithoutAStepBelongsToTheGoal() throws IOException {
        EvidenceView view = service.add(goal.id(), details(null, "Essay", TODAY, null, null), file("essay.docx"));

        assertThat(attachments.forOwner(RecordOwner.ofGoal(goal.id()))).extracting(AttachmentView::id)
                .containsExactly(view.attachment().id());
    }

    @Test
    void invalidEvidenceNeverCopiesTheFile() throws IOException {
        assertThatThrownBy(() -> service.add(goal.id(), details(step, "Future", TODAY.plusDays(1), null, null), file("a.txt")))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("future-date"));
        assertThatThrownBy(() -> service.add(goal.id(), details(step, " ", TODAY, null, null), file("b.txt")))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("required"));

        assertThat(storage.count()).isZero();
        assertThat(evidence.size()).isZero();
    }

    @Test
    void evidenceWithNoLinkFileOrNoteIsRejected() {
        assertThatThrownBy(() -> service.add(goal.id(), details(null, "Empty", TODAY, null, null), null))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("evidence-empty"));
    }

    @Test
    void theCopyIsRemovedWhenSavingTheEvidenceFails() throws IOException {
        evidence.failOnSave(true);

        assertThatThrownBy(() -> service.add(goal.id(), details(step, "Certificate", TODAY, null, null), file("cert.pdf")))
                .isInstanceOf(IllegalStateException.class);

        assertThat(storage.count()).isZero();
        assertThat(attachmentRows.size()).isZero();
    }

    @Test
    void unknownGoalsAndStepsAreNotFound() {
        assertThatThrownBy(() -> service.add(GoalId.newId(), details(null, "T", TODAY, null, "n"), null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.add(goal.id(), details(StepId.newId(), "T", TODAY, null, "n"), null))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.delete(EvidenceId.newId())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateChangesTheDetails() {
        EvidenceView added = service.add(goal.id(), details(null, "Portfolio", TODAY, null, "Deployed"), null);

        EvidenceView updated = service.update(added.id(), details(step, "Portfolio v2", TODAY.minusDays(2), "example.com", null));

        assertThat(updated.title()).isEqualTo("Portfolio v2");
        assertThat(updated.url()).isEqualTo("https://example.com");
        assertThat(updated.stepTitle()).isEqualTo("Grammar");
        assertThat(service.forStep(step)).extracting(EvidenceView::id).containsExactly(added.id());
    }

    @Test
    void movingEvidenceToAnotherStepMovesItsFile() throws IOException {
        EvidenceView added = service.add(goal.id(), details(step, "Certificate", TODAY, null, null), file("cert.pdf"));

        service.update(added.id(), details(otherStep, "Certificate", TODAY, null, null));

        assertThat(attachments.forOwner(RecordOwner.ofStep(step))).isEmpty();
        assertThat(attachments.forOwner(RecordOwner.ofStep(otherStep))).hasSize(1);
    }

    @Test
    void updateRejectsAStepOfAnotherGoal() {
        EvidenceView added = service.add(goal.id(), details(null, "Portfolio", TODAY, null, "Deployed"), null);

        assertThatThrownBy(() -> service.update(added.id(), details(StepId.newId(), "Portfolio", TODAY, null, "n")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void replaceFileStoresTheNewOneAndDeletesTheOld() throws IOException {
        EvidenceView added = service.add(goal.id(), details(step, "Recording", TODAY, null, null), file("v1.mp3"));

        EvidenceView replaced = service.replaceFile(added.id(), file("v2.mp3"));

        assertThat(replaced.attachment().name()).isEqualTo("v2.mp3");
        assertThat(replaced.attachment().id()).isNotEqualTo(added.attachment().id());
        assertThat(storage.count()).isEqualTo(1);
        assertThat(attachmentRows.size()).isEqualTo(1);
    }

    @Test
    void replaceFileCanAddAFileToEvidenceWithoutOne() throws IOException {
        EvidenceView added = service.add(goal.id(), details(null, "Essay", TODAY, null, "Draft"), null);

        EvidenceView replaced = service.replaceFile(added.id(), file("essay.docx"));

        assertThat(replaced.attachment().name()).isEqualTo("essay.docx");
    }

    @Test
    void removeFileDeletesTheAttachment() throws IOException {
        EvidenceView added = service.add(goal.id(), details(step, "Recording", TODAY, null, "Good take"), file("v1.mp3"));

        EvidenceView removed = service.removeFile(added.id());

        assertThat(removed.attachment()).isNull();
        assertThat(storage.count()).isZero();
        assertThat(attachmentRows.size()).isZero();
        assertThat(service.forGoal(goal.id()).getFirst().attachment()).isNull();
    }

    @Test
    void removeFileIsRefusedWhenTheFileIsAllThereIs() throws IOException {
        EvidenceView added = service.add(goal.id(), details(step, "Certificate", TODAY, null, null), file("cert.pdf"));

        assertThatThrownBy(() -> service.removeFile(added.id()))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("evidence-empty"));

        assertThat(storage.count()).isEqualTo(1);
        assertThat(service.forGoal(goal.id()).getFirst().attachment()).isNotNull();
    }

    @Test
    void deleteRemovesTheEvidenceAndItsFile() throws IOException {
        EvidenceView added = service.add(goal.id(), details(step, "Certificate", TODAY, null, null), file("cert.pdf"));

        service.delete(added.id());

        assertThat(service.forGoal(goal.id())).isEmpty();
        assertThat(storage.count()).isZero();
        assertThat(attachmentRows.size()).isZero();
    }

    @Test
    void forGoalAndForStepListNewestFirst() {
        EvidenceView old = service.add(goal.id(), details(step, "Old", TODAY.minusDays(10), null, "n"), null);
        EvidenceView recent = service.add(goal.id(), details(step, "Recent", TODAY.minusDays(1), null, "n"), null);
        EvidenceView other = service.add(goal.id(), details(otherStep, "Other", TODAY, null, "n"), null);

        assertThat(service.forGoal(goal.id())).extracting(EvidenceView::id)
                .containsExactly(other.id(), recent.id(), old.id());
        assertThat(service.forStep(step)).extracting(EvidenceView::id).containsExactly(recent.id(), old.id());
        assertThat(service.forStep(StepId.newId())).isEmpty();
    }

    @Test
    void forGoalOfAnUnknownGoalIsNotFound() {
        assertThatThrownBy(() -> service.forGoal(GoalId.newId())).isInstanceOf(NotFoundException.class);
    }
}
