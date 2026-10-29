package com.edutrack.desktop.ui.common;

/** The outcome of an action the learner started (an export, an import): a message to show and whether it worked. */
public record ActionResult(boolean success, String message) {

    public static ActionResult ok(String message) {
        return new ActionResult(true, message);
    }

    public static ActionResult failed(String message) {
        return new ActionResult(false, message);
    }
}
