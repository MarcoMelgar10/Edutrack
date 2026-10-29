package com.edutrack.desktop.ui.shell;

import com.edutrack.desktop.ui.settings.UiPreferences;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.springframework.stereotype.Component;

/**
 * What of the window's chrome is hidden so the page being worked on can fill it. Three independent
 * switches: the sidebar can be folded away (remembered between runs), the open step page can ask for
 * the goal list to make room (until switched off), and focus mode hides all of it at once. One
 * instance serves the whole window, so it survives the shell being rebuilt after a language switch.
 */
@Component
public class FocusMode {

    private final BooleanProperty sidebarCollapsed = new SimpleBooleanProperty();
    private final BooleanProperty focus = new SimpleBooleanProperty();
    private final BooleanProperty stepExpanded = new SimpleBooleanProperty();

    public FocusMode(UiPreferences preferences) {
        sidebarCollapsed.set(preferences.isSidebarCollapsed());
        sidebarCollapsed.subscribe(preferences::setSidebarCollapsed);
    }

    // ---- sidebar ----

    /** The sidebar is folded to its toggle button (ignored while focus mode hides it altogether). */
    public ReadOnlyBooleanProperty sidebarCollapsedProperty() {
        return sidebarCollapsed;
    }

    public void toggleSidebar() {
        sidebarCollapsed.set(!sidebarCollapsed.get());
    }

    // ---- step page ----

    /** The open step page has asked for the goal list to be hidden. */
    public ReadOnlyBooleanProperty stepExpandedProperty() {
        return stepExpanded;
    }

    public void toggleStepExpanded() {
        stepExpanded.set(!stepExpanded.get());
    }

    /**
     * True while the goal list should be hidden: in focus mode, or while a step page that asked for
     * room is open. {@code stepOpen} is the area's own step page, so other areas keep their list.
     */
    public BooleanBinding goalListHidden(ObservableBooleanValue stepOpen) {
        return focus.or(stepExpanded.and(stepOpen));
    }

    // ---- focus mode ----

    public ReadOnlyBooleanProperty focusProperty() {
        return focus;
    }

    public boolean isFocus() {
        return focus.get();
    }

    public void toggleFocus() {
        focus.set(!focus.get());
    }

    public void exitFocus() {
        focus.set(false);
    }

    /** The sidebar is shown folded to its toggle button. */
    public BooleanBinding sidebarCompact() {
        return sidebarCollapsed.and(focus.not());
    }
}
