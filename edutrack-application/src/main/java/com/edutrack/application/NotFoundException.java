package com.edutrack.application;

/** The requested area, goal or step no longer exists (for example, it was deleted meanwhile). */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String what, Object id) {
        super(what + " not found: " + id);
    }
}
