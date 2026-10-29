package com.edutrack.desktop.ui.shell;

import com.edutrack.desktop.ui.settings.UiPreferences;
import javafx.beans.property.SimpleBooleanProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;

class FocusModeTest {

    private final Preferences node = Preferences.userRoot().node("edutrack-test/" + UUID.randomUUID());

    @AfterEach
    void removeNode() throws BackingStoreException {
        node.removeNode();
    }

    private FocusMode fresh() {
        return new FocusMode(new UiPreferences(node));
    }

    @Test
    void startsWithEverythingShown() {
        FocusMode mode = fresh();

        assertThat(mode.sidebarCollapsedProperty().get()).isFalse();
        assertThat(mode.isFocus()).isFalse();
        assertThat(mode.sidebarCompact().get()).isFalse();
        assertThat(mode.goalListHidden(new SimpleBooleanProperty(true)).get()).isFalse();
    }

    @Test
    void sidebarStateIsRemembered() {
        fresh().toggleSidebar();

        FocusMode later = fresh();
        assertThat(later.sidebarCollapsedProperty().get()).isTrue();
        assertThat(later.sidebarCompact().get()).isTrue();

        later.toggleSidebar();
        assertThat(fresh().sidebarCollapsedProperty().get()).isFalse();
    }

    @Test
    void focusModeIsNotRemembered() {
        FocusMode mode = fresh();
        mode.toggleFocus();

        assertThat(mode.isFocus()).isTrue();
        assertThat(fresh().isFocus()).isFalse();
    }

    @Test
    void focusModeShowsNoCompactSidebarAndHidesTheGoalListEverywhere() {
        FocusMode mode = fresh();
        mode.toggleSidebar();
        var stepOpen = new SimpleBooleanProperty(false);
        var hidden = mode.goalListHidden(stepOpen);

        mode.toggleFocus();

        assertThat(mode.sidebarCompact().get()).isFalse();
        assertThat(hidden.get()).isTrue();

        mode.exitFocus();

        assertThat(mode.sidebarCompact().get()).isTrue();
        assertThat(hidden.get()).isFalse();
        mode.exitFocus();
        assertThat(mode.isFocus()).isFalse();
    }

    @Test
    void expandedStepHidesTheGoalListOnlyWhileAStepIsOpen() {
        FocusMode mode = fresh();
        var stepOpen = new SimpleBooleanProperty(false);
        var hidden = mode.goalListHidden(stepOpen);

        mode.toggleStepExpanded();
        assertThat(hidden.get()).isFalse();

        stepOpen.set(true);
        assertThat(hidden.get()).isTrue();

        stepOpen.set(false);
        assertThat(hidden.get()).isFalse();

        mode.toggleStepExpanded();
        stepOpen.set(true);
        assertThat(hidden.get()).isFalse();
    }
}
