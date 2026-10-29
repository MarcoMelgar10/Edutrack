package com.edutrack.application.evidence;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.attachment.AttachmentService;
import com.edutrack.application.attachment.AttachmentView;
import com.edutrack.domain.attachment.AttachmentId;
import com.edutrack.domain.evidence.Evidence;
import com.edutrack.domain.evidence.EvidenceDetails;
import com.edutrack.domain.evidence.EvidenceId;
import com.edutrack.domain.evidence.EvidenceRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.record.RecordOwner;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Use cases for evidence of learning: projects, recordings, essays, certificates. A file given with
 * the evidence is stored as an attachment owned by the evidence's step (or by the goal when it has
 * no step) and is deleted with the evidence.
 *
 * <p>Deleting a goal or step leaves the stored files behind; {@link AttachmentService#removeOrphans()}
 * cleans them at the next start-up.
 */
public class EvidenceService {

    private final EvidenceRepository evidence;
    private final AttachmentService attachments;
    private final GoalRepository goals;
    private final Clock clock;

    public EvidenceService(EvidenceRepository evidence, AttachmentService attachments, GoalRepository goals,
                           Clock clock) {
        this.evidence = evidence;
        this.attachments = attachments;
        this.goals = goals;
        this.clock = clock;
    }

    /**
     * Records evidence for a goal. {@code file} may be {@code null}. Everything is validated before
     * the file is copied, and the copy is removed again if saving fails.
     */
    public EvidenceView add(GoalId goalId, EvidenceDetails details, Path file) {
        Goal goal = loadGoal(goalId);
        requireStepOf(goal, details.stepId());
        // A stand-in id lets the "link, file or note" rule see the file before it is copied.
        Evidence created = Evidence.create(goalId, details, file == null ? null : AttachmentId.newId(),
                LocalDate.now(clock), clock.instant());
        if (file == null) {
            return view(evidence.save(created), goal);
        }
        AttachmentView stored = attachments.add(ownerOf(created), file);
        created.attach(stored.id());
        return view(saveOrDiscard(created, stored.id()), goal);
    }

    public EvidenceView update(EvidenceId id, EvidenceDetails details) {
        Evidence existing = load(id);
        Goal goal = loadGoal(existing.goalId());
        requireStepOf(goal, details.stepId());
        existing.update(details, LocalDate.now(clock));
        evidence.save(existing);
        if (existing.attachmentId() != null) {
            attachments.moveTo(existing.attachmentId(), ownerOf(existing));
        }
        return view(existing, goal);
    }

    /** Stores the new file, points the evidence at it and then deletes the previous file, if any. */
    public EvidenceView replaceFile(EvidenceId id, Path file) {
        Evidence existing = load(id);
        AttachmentId previous = existing.attachmentId();
        AttachmentView stored = attachments.add(ownerOf(existing), file);
        existing.attach(stored.id());
        saveOrDiscard(existing, stored.id());
        if (previous != null) {
            attachments.delete(previous);
        }
        return view(existing, loadGoal(existing.goalId()));
    }

    /** Refused with {@code evidence-empty} when the file was all the evidence had. */
    public EvidenceView removeFile(EvidenceId id) {
        Evidence existing = load(id);
        AttachmentId previous = existing.attachmentId();
        existing.detach();
        evidence.save(existing);
        if (previous != null) {
            attachments.delete(previous);
        }
        return view(existing, loadGoal(existing.goalId()));
    }

    public void delete(EvidenceId id) {
        Evidence existing = load(id);
        evidence.delete(id);
        if (existing.attachmentId() != null) {
            attachments.delete(existing.attachmentId());
        }
    }

    /** Evidence of a goal, newest first. */
    public List<EvidenceView> forGoal(GoalId goalId) {
        Goal goal = loadGoal(goalId);
        return evidence.findByGoal(goalId).stream().map(item -> view(item, goal)).toList();
    }

    /** Evidence linked to a step, newest first. */
    public List<EvidenceView> forStep(StepId stepId) {
        List<Evidence> items = evidence.findByStep(stepId);
        Map<GoalId, Goal> loaded = new HashMap<>();
        return items.stream()
                .map(item -> view(item, loaded.computeIfAbsent(item.goalId(), this::loadGoal)))
                .toList();
    }

    private Evidence saveOrDiscard(Evidence toSave, AttachmentId newFile) {
        try {
            return evidence.save(toSave);
        } catch (RuntimeException failure) {
            attachments.delete(newFile);
            throw failure;
        }
    }

    private EvidenceView view(Evidence item, Goal goal) {
        String stepTitle = item.stepId() == null ? null : goal.steps().stream()
                .filter(step -> step.id().equals(item.stepId()))
                .map(RoadmapStep::title)
                .findFirst()
                .orElse(null);
        AttachmentView file = item.attachmentId() == null ? null : attachments.find(item.attachmentId()).orElse(null);
        return EvidenceView.of(item, stepTitle, file);
    }

    private static RecordOwner ownerOf(Evidence item) {
        return item.stepId() == null ? RecordOwner.ofGoal(item.goalId()) : RecordOwner.ofStep(item.stepId());
    }

    private static void requireStepOf(Goal goal, StepId stepId) {
        if (stepId != null && goal.steps().stream().noneMatch(step -> Objects.equals(step.id(), stepId))) {
            throw new NotFoundException("Step", stepId);
        }
    }

    private Evidence load(EvidenceId id) {
        return evidence.findById(id).orElseThrow(() -> new NotFoundException("Evidence", id));
    }

    private Goal loadGoal(GoalId id) {
        return goals.findById(id).orElseThrow(() -> new NotFoundException("Goal", id));
    }
}
