package com.edutrack.desktop.ui.shell;

/**
 * Lets a feature put a number on its entry in the left rail (cards due, routines pending, …).
 * Declare an implementation as a Spring bean and the shell picks it up: it asks every source for its
 * {@link #count()} whenever data changes and when the window gains focus. A count of 0 hides the badge.
 */
public interface NavBadgeSource {

    /** The rail entry that shows the badge. */
    ShellPage page();

    /** The number to show; called on the FX thread, so it must be quick. */
    int count();
}
