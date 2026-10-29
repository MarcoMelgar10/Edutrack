package com.edutrack.desktop.ui.session;

import com.edutrack.domain.goal.StepId;

/** A step in a step box; {@code id} is {@code null} for "No specific step". */
public record StepChoice(StepId id, String label) {

    @Override
    public String toString() {
        return label;
    }
}
