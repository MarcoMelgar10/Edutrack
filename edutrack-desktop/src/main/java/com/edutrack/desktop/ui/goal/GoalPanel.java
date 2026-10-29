package com.edutrack.desktop.ui.goal;

import com.edutrack.application.goal.GoalDetails;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;

/**
 * One tab of the goal page beyond the roadmap and the results: practice, skills, cards, evidence.
 * Each is a prototype Spring bean ordered with {@code @Order}; the goal page creates one of each, adds
 * its {@linkplain #content() content} as a tab and hands it the goal every time the goal is (re)loaded.
 */
public interface GoalPanel {

    /** The tab text (it may carry a count and follow the data). */
    ObservableValue<String> title();

    Node content();

    /** The goal now shown; called again after every change to it or to anything recorded on it. */
    void show(GoalDetails goal);

    /** The goal page was closed or the goal deleted: stop following the data. */
    default void clear() {
    }
}
