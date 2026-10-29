package com.edutrack.desktop.ui.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Work typed but not yet saved (e.g. notes waiting for the pause in typing that triggers the
 * autosave). The application calls {@link #flushAll()} on shutdown so closing the window right
 * after typing loses nothing.
 *
 * <p>Sources are held weakly: a view model that goes away with its view needs no unregistering.
 */
@Component
public class PendingWork {

    /** Something that may hold unsaved changes. Called on the FX thread. */
    public interface Source {
        void flushPending();
    }

    private static final Logger log = LoggerFactory.getLogger(PendingWork.class);

    private final Set<Source> sources = Collections.newSetFromMap(new WeakHashMap<>());

    public void track(Source source) {
        sources.add(source);
    }

    /** Saves what every live source still holds; one failure does not stop the others. */
    public void flushAll() {
        for (Source source : new ArrayList<>(sources)) {
            try {
                source.flushPending();
            } catch (RuntimeException failure) {
                log.error("Could not save pending changes on shutdown", failure);
            }
        }
    }
}
