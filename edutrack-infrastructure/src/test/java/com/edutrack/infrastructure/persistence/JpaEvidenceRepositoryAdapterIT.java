package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.skill.Competency;
import com.edutrack.domain.skill.CompetencyDetails;
import com.edutrack.domain.skill.CompetencyId;
import com.edutrack.domain.skill.CompetencyRepository;
import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.attachment.AttachmentRepository;
import com.edutrack.domain.evidence.Evidence;
import com.edutrack.domain.evidence.EvidenceDetails;
import com.edutrack.domain.evidence.EvidenceKind;
import com.edutrack.domain.evidence.EvidenceRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.RecordOwner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaAttachmentRepositoryAdapter.class,
        JpaEvidenceRepositoryAdapter.class, JpaCompetencyRepositoryAdapter.class})
class JpaEvidenceRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-03-01T09:30:15.123456Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 12, 31);

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private AttachmentRepository attachments;

    @Autowired
    private EvidenceRepository evidence;

    @Autowired
    private CompetencyRepository competencies;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void roundTripsEvidenceWithEverythingSet() {
        Goal goal = saveGoalWithSteps(1);
        StepId step = goal.steps().getFirst().id();
        Attachment file = attachments.save(attachment(RecordOwner.ofStep(step), "talk.mp3"));
        CompetencyId competency = competencies.save(Competency.create(goal.id(),
                new CompetencyDetails("Speaking", null, 4), 0, NOW)).id();
        Evidence created = Evidence.create(goal.id(), new EvidenceDetails(step, EvidenceKind.RECORDING, "Talk",
                LocalDate.of(2026, 3, 2), "example.com/talk", "Went well", competency), file.id(), TODAY, NOW);

        Evidence saved = evidence.save(created);
        flushAndClear();
        Evidence loaded = evidence.findById(created.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(created);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(created);
        assertThat(loaded.url()).isEqualTo("https://example.com/talk");
        assertThat(loaded.attachmentId()).isEqualTo(file.id());
        assertThat(loaded.competencyId()).isEqualTo(competency);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
    }

    @Test
    void roundTripsMinimalEvidence() {
        Goal goal = saveGoalWithSteps(0);
        Evidence created = Evidence.create(goal.id(), new EvidenceDetails(null, EvidenceKind.OTHER, "Note only",
                LocalDate.of(2026, 3, 2), null, "Just a note", null), null, TODAY, NOW);

        evidence.save(created);
        flushAndClear();
        Evidence loaded = evidence.findById(created.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(created);
        assertThat(loaded.stepId()).isNull();
        assertThat(loaded.attachmentId()).isNull();
        assertThat(loaded.competencyId()).isNull();
    }

    @Test
    void savesAnEditedEvidenceAndItsFileChanges() {
        Goal goal = saveGoalWithSteps(1);
        Attachment file = attachments.save(attachment(RecordOwner.ofGoal(goal.id()), "v1.pdf"));
        Evidence created = evidence.save(Evidence.create(goal.id(), new EvidenceDetails(null, EvidenceKind.ESSAY, "Essay",
                LocalDate.of(2026, 3, 2), null, "Draft", null), file.id(), TODAY, NOW));
        flushAndClear();

        Evidence loaded = evidence.findById(created.id()).orElseThrow();
        loaded.update(new EvidenceDetails(goal.steps().getFirst().id(), EvidenceKind.ESSAY, "Essay v2",
                LocalDate.of(2026, 3, 5), "https://example.com", null, null), TODAY);
        loaded.detach();
        evidence.save(loaded);
        flushAndClear();
        Evidence reloaded = evidence.findById(created.id()).orElseThrow();

        assertThat(reloaded).usingRecursiveComparison().isEqualTo(loaded);
        assertThat(reloaded.title()).isEqualTo("Essay v2");
        assertThat(reloaded.attachmentId()).isNull();
        assertThat(reloaded.note()).isNull();
    }

    @Test
    void findByGoalAndByStepReturnNewestFirst() {
        Goal goal = saveGoalWithSteps(2);
        StepId first = goal.steps().get(0).id();
        StepId second = goal.steps().get(1).id();
        Goal otherGoal = saveGoalWithSteps(1);
        Evidence old = evidence.save(evidence(goal, first, "Old", LocalDate.of(2026, 1, 10), NOW));
        Evidence recent = evidence.save(evidence(goal, first, "Recent", LocalDate.of(2026, 2, 20), NOW));
        Evidence sameDayLater = evidence.save(evidence(goal, second, "Same day, added later", LocalDate.of(2026, 2, 20), NOW.plusSeconds(60)));
        Evidence withoutStep = evidence.save(evidence(goal, null, "Whole goal", LocalDate.of(2026, 3, 1), NOW));
        evidence.save(evidence(otherGoal, otherGoal.steps().getFirst().id(), "Other goal", LocalDate.of(2026, 3, 1), NOW));
        flushAndClear();

        assertThat(evidence.findByGoal(goal.id())).extracting(Evidence::id)
                .containsExactly(withoutStep.id(), sameDayLater.id(), recent.id(), old.id());
        assertThat(evidence.findByStep(first)).extracting(Evidence::id).containsExactly(recent.id(), old.id());
        assertThat(evidence.findByStep(second)).extracting(Evidence::id).containsExactly(sameDayLater.id());
        assertThat(evidence.findByStep(StepId.newId())).isEmpty();
    }

    @Test
    void deleteRemovesTheEvidenceButNotItsAttachment() {
        Goal goal = saveGoalWithSteps(0);
        Attachment file = attachments.save(attachment(RecordOwner.ofGoal(goal.id()), "a.pdf"));
        Evidence created = evidence.save(Evidence.create(goal.id(), new EvidenceDetails(null, EvidenceKind.CERTIFICATE,
                "Cert", LocalDate.of(2026, 3, 2), null, null, null), file.id(), TODAY, NOW));
        flushAndClear();

        evidence.delete(created.id());
        flushAndClear();

        assertThat(evidence.findById(created.id())).isEmpty();
        assertThat(attachments.findById(file.id())).isPresent();
    }

    @Test
    void deletingTheAttachmentClearsTheReferenceAndKeepsTheEvidence() {
        Goal goal = saveGoalWithSteps(0);
        Attachment file = attachments.save(attachment(RecordOwner.ofGoal(goal.id()), "a.pdf"));
        Evidence created = evidence.save(Evidence.create(goal.id(), new EvidenceDetails(null, EvidenceKind.CERTIFICATE,
                "Cert", LocalDate.of(2026, 3, 2), null, "Passed", null), file.id(), TODAY, NOW));
        flushAndClear();

        attachments.delete(file.id());
        flushAndClear();

        Evidence loaded = evidence.findById(created.id()).orElseThrow();
        assertThat(loaded.attachmentId()).isNull();
        assertThat(loaded.title()).isEqualTo("Cert");
    }

    @Test
    void deletingTheStepKeepsTheEvidenceWithoutAStep() {
        Goal goal = saveGoalWithSteps(2);
        StepId removed = goal.steps().getFirst().id();
        StepId kept = goal.steps().getLast().id();
        Evidence onRemoved = evidence.save(evidence(goal, removed, "On removed step", LocalDate.of(2026, 3, 1), NOW));
        Evidence onKept = evidence.save(evidence(goal, kept, "On kept step", LocalDate.of(2026, 3, 1), NOW));
        flushAndClear();

        Goal loaded = goals.findById(goal.id()).orElseThrow();
        loaded.removeStep(removed);
        goals.save(loaded);
        flushAndClear();

        assertThat(evidence.findById(onRemoved.id()).orElseThrow().stepId()).isNull();
        assertThat(evidence.findById(onKept.id()).orElseThrow().stepId()).isEqualTo(kept);
    }

    @Test
    void evidenceThatLostItsOnlyFileStillLoads() {
        Goal goal = saveGoalWithSteps(1);
        StepId step = goal.steps().getFirst().id();
        Attachment file = attachments.save(attachment(RecordOwner.ofStep(step), "cert.pdf"));
        Evidence created = evidence.save(Evidence.create(goal.id(), new EvidenceDetails(step, EvidenceKind.CERTIFICATE,
                "Cert", LocalDate.of(2026, 3, 2), null, null, null), file.id(), TODAY, NOW));
        flushAndClear();

        Goal loaded = goals.findById(goal.id()).orElseThrow();
        loaded.removeStep(step);
        goals.save(loaded);
        flushAndClear();

        Evidence reloaded = evidence.findById(created.id()).orElseThrow();
        assertThat(reloaded.attachmentId()).isNull();
        assertThat(reloaded.stepId()).isNull();
    }

    @Test
    void deletingTheGoalDeletesItsEvidence() {
        Goal goal = saveGoalWithSteps(1);
        Goal kept = saveGoalWithSteps(0);
        Evidence gone = evidence.save(evidence(goal, null, "Gone", LocalDate.of(2026, 3, 1), NOW));
        Evidence stays = evidence.save(evidence(kept, null, "Stays", LocalDate.of(2026, 3, 1), NOW));
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(evidence.findById(gone.id())).isEmpty();
        assertThat(evidence.findById(stays.id())).isPresent();
    }

    // ---------- helpers ----------

    private Goal saveGoalWithSteps(int steps) {
        Area area = areas.save(Area.create("Area " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW));
        Goal goal = Goal.create(area.id(), "Reach B2", null, null, 0, NOW);
        for (int i = 1; i <= steps; i++) {
            goal.addStep(new StepDetails("Step " + i, null, null), StepStatus.NOT_STARTED, NOW);
        }
        Goal saved = goals.save(goal);
        entityManager.flush();
        return saved;
    }

    private static Attachment attachment(RecordOwner owner, String name) {
        AttachmentId id = AttachmentId.newId();
        return Attachment.create(id, owner, name, id + "/" + name, null, 100, NOW);
    }

    private static Evidence evidence(Goal goal, StepId step, String title, LocalDate takenOn, Instant createdAt) {
        return Evidence.create(goal.id(), new EvidenceDetails(step, EvidenceKind.PROJECT, title, takenOn, null,
                "Note", null), null, TODAY, createdAt);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
