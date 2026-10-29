package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.attachment.Attachment;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.attachment.AttachmentRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.RecordOwner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaAttachmentRepositoryAdapter.class})
class JpaAttachmentRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-03-01T09:30:15.123456Z");

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private AttachmentRepository attachments;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void roundTripsAStepAttachment() {
        Goal goal = saveGoalWithSteps(1);
        RecordOwner owner = RecordOwner.ofStep(goal.steps().getFirst().id());
        Attachment attachment = attachment(owner, "certificate.pdf", NOW);

        Attachment saved = attachments.save(attachment);
        flushAndClear();
        Attachment loaded = attachments.findById(attachment.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(attachment);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(attachment);
        assertThat(loaded.owner().isStep()).isTrue();
        assertThat(loaded.mimeType()).isEqualTo("application/pdf");
        assertThat(loaded.sizeBytes()).isEqualTo(2048);
    }

    @Test
    void roundTripsAGoalAttachmentWithoutMimeType() {
        Goal goal = saveGoalWithSteps(0);
        RecordOwner owner = RecordOwner.ofGoal(goal.id());
        Attachment attachment = Attachment.create(AttachmentId.newId(), owner, "notes", "x/notes", null, 1, NOW);

        attachments.save(attachment);
        flushAndClear();
        Attachment loaded = attachments.findById(attachment.id()).orElseThrow();

        assertThat(loaded.owner()).isEqualTo(owner);
        assertThat(loaded.owner().isStep()).isFalse();
        assertThat(loaded.mimeType()).isNull();
    }

    @Test
    void unknownIdIsEmpty() {
        assertThat(attachments.findById(AttachmentId.newId())).isEmpty();
    }

    @Test
    void findByOwnerReturnsNewestFirstAndOnlyThatOwners() {
        Goal goal = saveGoalWithSteps(2);
        RecordOwner first = RecordOwner.ofStep(goal.steps().get(0).id());
        RecordOwner second = RecordOwner.ofStep(goal.steps().get(1).id());
        RecordOwner goalOwner = RecordOwner.ofGoal(goal.id());
        Attachment old = attachments.save(attachment(first, "old.txt", NOW));
        Attachment recent = attachments.save(attachment(first, "recent.txt", NOW.plusSeconds(60)));
        attachments.save(attachment(second, "other.txt", NOW));
        attachments.save(attachment(goalOwner, "goal.txt", NOW));
        flushAndClear();

        assertThat(attachments.findByOwner(first)).extracting(Attachment::id).containsExactly(recent.id(), old.id());
        assertThat(attachments.findByOwner(second)).hasSize(1);
        assertThat(attachments.findByOwner(goalOwner)).extracting(Attachment::originalName).containsExactly("goal.txt");
    }

    @Test
    void movingAnAttachmentToAnotherOwnerIsSaved() {
        Goal goal = saveGoalWithSteps(2);
        RecordOwner first = RecordOwner.ofStep(goal.steps().get(0).id());
        RecordOwner second = RecordOwner.ofStep(goal.steps().get(1).id());
        Attachment attachment = attachments.save(attachment(first, "a.txt", NOW));
        flushAndClear();

        Attachment loaded = attachments.findById(attachment.id()).orElseThrow();
        loaded.moveTo(second);
        attachments.save(loaded);
        flushAndClear();

        assertThat(attachments.findByOwner(first)).isEmpty();
        assertThat(attachments.findByOwner(second)).hasSize(1);
    }

    @Test
    void deleteRemovesTheRow() {
        Goal goal = saveGoalWithSteps(1);
        Attachment attachment = attachments.save(attachment(RecordOwner.ofGoal(goal.id()), "a.txt", NOW));
        flushAndClear();

        attachments.delete(attachment.id());
        flushAndClear();

        assertThat(attachments.findById(attachment.id())).isEmpty();
    }

    @Test
    void allStoredPathsListsEveryRow() {
        Goal goal = saveGoalWithSteps(1);
        Attachment one = attachments.save(attachment(RecordOwner.ofGoal(goal.id()), "one.txt", NOW));
        Attachment two = attachments.save(attachment(RecordOwner.ofStep(goal.steps().getFirst().id()), "two.txt", NOW));
        flushAndClear();

        assertThat(attachments.allStoredPaths()).contains(one.storedPath(), two.storedPath());
    }

    @Test
    void theSameStoredPathCannotBeUsedTwice() {
        Goal goal = saveGoalWithSteps(0);
        RecordOwner owner = RecordOwner.ofGoal(goal.id());
        attachments.save(Attachment.create(AttachmentId.newId(), owner, "a.txt", "dup/a.txt", null, 1, NOW));

        assertThatThrownBy(() -> attachments.save(
                Attachment.create(AttachmentId.newId(), owner, "a.txt", "dup/a.txt", null, 1, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAGoalDeletesItsAttachmentRowsAndItsStepsRows() {
        Goal goal = saveGoalWithSteps(1);
        Goal kept = saveGoalWithSteps(1);
        Attachment onGoal = attachments.save(attachment(RecordOwner.ofGoal(goal.id()), "goal.txt", NOW));
        Attachment onStep = attachments.save(attachment(RecordOwner.ofStep(goal.steps().getFirst().id()), "step.txt", NOW));
        Attachment other = attachments.save(attachment(RecordOwner.ofStep(kept.steps().getFirst().id()), "kept.txt", NOW));
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        assertThat(attachments.findById(onGoal.id())).isEmpty();
        assertThat(attachments.findById(onStep.id())).isEmpty();
        assertThat(attachments.findById(other.id())).isPresent();
    }

    @Test
    void deletingAStepDeletesItsAttachmentRows() {
        Goal goal = saveGoalWithSteps(2);
        Attachment onStep = attachments.save(attachment(RecordOwner.ofStep(goal.steps().getFirst().id()), "step.txt", NOW));
        Attachment other = attachments.save(attachment(RecordOwner.ofStep(goal.steps().getLast().id()), "other.txt", NOW));
        flushAndClear();

        Goal loaded = goals.findById(goal.id()).orElseThrow();
        loaded.removeStep(loaded.steps().getFirst().id());
        goals.save(loaded);
        flushAndClear();

        assertThat(attachments.findById(onStep.id())).isEmpty();
        assertThat(attachments.findById(other.id())).isPresent();
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

    private static Attachment attachment(RecordOwner owner, String name, Instant addedAt) {
        AttachmentId id = AttachmentId.newId();
        return Attachment.create(id, owner, name, id + "/" + name, "application/pdf", 2048, addedAt);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }
}
