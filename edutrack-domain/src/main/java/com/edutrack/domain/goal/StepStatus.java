package com.edutrack.domain.goal;

public enum StepStatus {
    NOT_STARTED,
    IN_PROGRESS,
    DONE;

    /** The status a click on the timeline mark moves to: Not started → In progress → Done → Not started. */
    public StepStatus next() {
        return switch (this) {
            case NOT_STARTED -> IN_PROGRESS;
            case IN_PROGRESS -> DONE;
            case DONE -> NOT_STARTED;
        };
    }
}
