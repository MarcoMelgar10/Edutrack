package com.edutrack.domain.record;

import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.shared.DomainValidationException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Aggregate root: everything learned for one step (or, for results, one goal) —
 * Markdown notes, the resources used, the exercise checklist and formal results.
 *
 * <p>It is separate from the {@code Goal} aggregate on purpose: notes can grow large and are only
 * needed when a step is opened, while goals are loaded on every screen. A record is created the
 * first time something is saved for its owner and is deleted with it (database cascade).
 */
public final class LearningRecord {

    public static final int NOTES_MAX_LENGTH = 200_000;

    private final LearningRecordId id;
    private final RecordOwner owner;
    private final List<Resource> resources;
    private final List<Exercise> exercises;
    private final List<Assessment> assessments;
    private String notes;
    private Instant updatedAt;

    private LearningRecord(LearningRecordId id, RecordOwner owner, String notes, Instant updatedAt,
                           List<Resource> resources, List<Exercise> exercises, List<Assessment> assessments) {
        this.id = Objects.requireNonNull(id, "id");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.notes = notes == null ? "" : notes;
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.resources = new ArrayList<>(resources);
        this.exercises = new ArrayList<>(exercises);
        this.assessments = new ArrayList<>(assessments);
    }

    public static LearningRecord create(RecordOwner owner, Instant now) {
        return new LearningRecord(LearningRecordId.newId(), owner, "", now, List.of(), List.of(), List.of());
    }

    /** Rebuilds a record from storage; the lists must already be in display order. */
    public static LearningRecord restore(LearningRecordId id, RecordOwner owner, String notes, Instant updatedAt,
                                         List<Resource> resources, List<Exercise> exercises,
                                         List<Assessment> assessments) {
        return new LearningRecord(id, owner, notes, updatedAt, resources, exercises, assessments);
    }

    // ---------- notes ----------

    /** Stores the Markdown exactly as written (no trimming: whitespace is formatting). */
    public void updateNotes(String markdown, Instant now) {
        String text = markdown == null ? "" : markdown;
        if (text.length() > NOTES_MAX_LENGTH) {
            throw new DomainValidationException("too-long", "Notes must be at most " + NOTES_MAX_LENGTH + " characters.", "Notes", NOTES_MAX_LENGTH);
        }
        notes = text;
        touch(now);
    }

    // ---------- resources ----------

    public Resource addResource(ResourceDetails details, Instant now) {
        Resource resource = Resource.create(details);
        resources.add(resource);
        touch(now);
        return resource;
    }

    public void updateResource(ResourceId resourceId, ResourceDetails details, Instant now) {
        find(resources, Resource::id, resourceId).update(details);
        touch(now);
    }

    public void markResourceReviewed(ResourceId resourceId, boolean reviewed, Instant now) {
        find(resources, Resource::id, resourceId).markReviewed(reviewed);
        touch(now);
    }

    public void removeResource(ResourceId resourceId, Instant now) {
        resources.remove(find(resources, Resource::id, resourceId));
        touch(now);
    }

    // ---------- exercises ----------

    public Exercise addExercise(String title, Instant now) {
        Exercise exercise = Exercise.create(title);
        exercises.add(exercise);
        touch(now);
        return exercise;
    }

    public void renameExercise(ExerciseId exerciseId, String title, Instant now) {
        find(exercises, Exercise::id, exerciseId).rename(title);
        touch(now);
    }

    public void markExerciseDone(ExerciseId exerciseId, boolean done, Instant now) {
        find(exercises, Exercise::id, exerciseId).markDone(done, now);
        touch(now);
    }

    public void removeExercise(ExerciseId exerciseId, Instant now) {
        exercises.remove(find(exercises, Exercise::id, exerciseId));
        touch(now);
    }

    /** Swaps the exercise with its neighbour; moving past either end does nothing. */
    public void moveExercise(ExerciseId exerciseId, MoveDirection direction, Instant now) {
        int from = exercises.indexOf(find(exercises, Exercise::id, exerciseId));
        int to = direction == MoveDirection.UP ? from - 1 : from + 1;
        if (to >= 0 && to < exercises.size()) {
            Collections.swap(exercises, from, to);
            touch(now);
        }
    }

    // ---------- results ----------

    /** {@code today} is the learner's current date: results cannot be dated after it. */
    public Assessment addAssessment(AssessmentDetails details, LocalDate today, Instant now) {
        Assessment assessment = Assessment.create(details, today);
        assessments.add(assessment);
        touch(now);
        return assessment;
    }

    public void updateAssessment(AssessmentId assessmentId, AssessmentDetails details, LocalDate today, Instant now) {
        find(assessments, Assessment::id, assessmentId).update(details, today);
        touch(now);
    }

    public void removeAssessment(AssessmentId assessmentId, Instant now) {
        assessments.remove(find(assessments, Assessment::id, assessmentId));
        touch(now);
    }

    // ---------- summary ----------

    public RecordSummary summary() {
        int done = (int) exercises.stream().filter(Exercise::done).count();
        return new RecordSummary(!notes.isBlank(), resources.size(), done, exercises.size(), assessments.size());
    }

    private void touch(Instant now) {
        updatedAt = Objects.requireNonNull(now, "now");
    }

    private <T, I> T find(List<T> items, Function<T, I> idOf, I id) {
        return items.stream()
                .filter(item -> idOf.apply(item).equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(id + " is not part of record " + this.id));
    }

    // ---------- accessors ----------

    public LearningRecordId id() {
        return id;
    }

    public RecordOwner owner() {
        return owner;
    }

    public String notes() {
        return notes;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public List<Resource> resources() {
        return Collections.unmodifiableList(resources);
    }

    public List<Exercise> exercises() {
        return Collections.unmodifiableList(exercises);
    }

    public List<Assessment> assessments() {
        return Collections.unmodifiableList(assessments);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LearningRecord record && id.equals(record.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
