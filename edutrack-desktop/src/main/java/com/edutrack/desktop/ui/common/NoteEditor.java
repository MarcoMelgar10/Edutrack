package com.edutrack.desktop.ui.common;

import javafx.beans.property.StringProperty;
import javafx.scene.Node;

/**
 * The body editor of a Markdown note, as the screens that write notes see it: a node to place in the
 * layout and the Markdown text it edits. Screens obtain one from {@link NoteEditors#create()} and never
 * build a text control themselves, so the editor can be replaced (for example by the live-styled
 * single-pane editor) by changing that one factory.
 */
public interface NoteEditor {

    /** The control to put in the layout; it grows to the space it is given. */
    Node node();

    /** The Markdown being edited. Bind it to the view model's text property. */
    StringProperty textProperty();

    /** Text shown while the editor is empty. */
    void setPromptText(String prompt);

    /** Moves the keyboard focus into the editor. */
    void focusEditor();
}
