package com.edutrack.domain.record;

/** The counts the roadmap timeline shows under a step, e.g. "Notes · 3 resources · Exercises 2/5 · 1 result". */
public record RecordSummary(boolean hasNotes, int resourceCount, int exercisesDone, int exercisesTotal, int resultCount) {

    public static final RecordSummary EMPTY = new RecordSummary(false, 0, 0, 0, 0);

    /** Both summaries added up, e.g. everything recorded across a goal's steps. */
    public RecordSummary plus(RecordSummary other) {
        return new RecordSummary(hasNotes || other.hasNotes, resourceCount + other.resourceCount,
                exercisesDone + other.exercisesDone, exercisesTotal + other.exercisesTotal,
                resultCount + other.resultCount);
    }

    public boolean isEmpty() {
        return !hasNotes && resourceCount == 0 && exercisesTotal == 0 && resultCount == 0;
    }
}
