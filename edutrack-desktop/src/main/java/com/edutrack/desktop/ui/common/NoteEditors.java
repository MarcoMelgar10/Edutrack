package com.edutrack.desktop.ui.common;

import javafx.beans.property.StringProperty;
import javafx.scene.Node;

/**
 * The single place that decides which {@link NoteEditor} the app uses for note bodies outside the step page:
 * the unified, formatted-as-you-type editor of {@code ui.notes}. Callers (the Journal) only see the small
 * {@link NoteEditor} interface.
 */
public final class NoteEditors {

    private NoteEditors() {
    }

    /** A fresh editor for one note body. */
    public static NoteEditor create() {
        return new UnifiedNoteEditor();
    }

    private static final class UnifiedNoteEditor implements NoteEditor {

        private final com.edutrack.desktop.ui.notes.NoteEditor editor = new com.edutrack.desktop.ui.notes.NoteEditor();

        @Override
        public Node node() {
            return editor;
        }

        @Override
        public StringProperty textProperty() {
            return editor.markdownProperty();
        }

        @Override
        public void setPromptText(String prompt) {
            editor.setPromptText(prompt);
        }

        @Override
        public void focusEditor() {
            editor.focusEditor();
        }
    }
}
