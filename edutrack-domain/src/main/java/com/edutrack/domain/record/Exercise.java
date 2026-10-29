package com.edutrack.domain.record;

import com.edutrack.domain.shared.Validation;

import java.time.Instant;
import java.util.Objects;

/** A practice task on a step's checklist, e.g. "Write three essays". */
public final class Exercise {

    public static final int TITLE_MAX_LENGTH = 200;

    private final ExerciseId id;
    private String title;
    private boolean done;
    private Instant doneAt;

    private Exercise(ExerciseId id, String title) {
        this.id = Objects.requireNonNull(id, "id");
        rename(title);
    }

    static Exercise create(String title) {
        return new Exercise(ExerciseId.newId(), title);
    }

    public static Exercise restore(ExerciseId id, String title, boolean done, Instant doneAt) {
        Exercise exercise = new Exercise(id, title);
        exercise.done = done;
        exercise.doneAt = done ? doneAt : null;
        return exercise;
    }

    void rename(String title) {
        this.title = Validation.requiredText(title, "Exercise", TITLE_MAX_LENGTH);
    }

    /** Ticking records when; unticking clears it. Ticking an already done exercise keeps the first date. */
    void markDone(boolean done, Instant now) {
        if (done == this.done) {
            return;
        }
        this.done = done;
        this.doneAt = done ? Objects.requireNonNull(now, "now") : null;
    }

    public ExerciseId id() {
        return id;
    }

    public String title() {
        return title;
    }

    public boolean done() {
        return done;
    }

    public Instant doneAt() {
        return doneAt;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Exercise exercise && id.equals(exercise.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
