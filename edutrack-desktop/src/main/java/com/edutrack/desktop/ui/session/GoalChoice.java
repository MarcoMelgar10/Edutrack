package com.edutrack.desktop.ui.session;

import com.edutrack.domain.goal.GoalId;

/** A goal in a goal box: "English · IELTS 7.0". */
public record GoalChoice(GoalId id, String label) {

    @Override
    public String toString() {
        return label;
    }
}
