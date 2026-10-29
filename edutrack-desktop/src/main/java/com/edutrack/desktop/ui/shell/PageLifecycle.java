package com.edutrack.desktop.ui.shell;

/**
 * Optional: a page controller that implements this hears about its page being shown and discarded.
 * Pages are created once and kept, so {@link #shown()} is the place to reload what may have changed
 * and {@link #disposed()} the place to stop following data changes.
 */
public interface PageLifecycle {

    /** The page was just brought to the front. */
    default void shown() {
    }

    /** The shell that holds the page is being replaced (e.g. after a language switch). */
    default void disposed() {
    }
}
