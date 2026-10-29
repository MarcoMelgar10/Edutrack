package com.edutrack.desktop.ui.common;

import javafx.beans.property.ReadOnlyLongWrapper;
import javafx.util.Subscription;
import org.springframework.stereotype.Component;

/**
 * App-wide "something was saved" signal. A view model that changes data calls
 * {@link #markChanged()}; views that show derived numbers (tab headers, Overview, goal cards)
 * subscribe and reload. Subscribers must keep the returned {@link Subscription} and
 * unsubscribe when their view goes away (e.g. an area tab is closed).
 */
@Component
public class DataChanges {

    private final ReadOnlyLongWrapper revision = new ReadOnlyLongWrapper();

    public void markChanged() {
        revision.set(revision.get() + 1);
    }

    public Subscription subscribe(Runnable onChange) {
        return revision.subscribe((before, after) -> onChange.run());
    }
}
