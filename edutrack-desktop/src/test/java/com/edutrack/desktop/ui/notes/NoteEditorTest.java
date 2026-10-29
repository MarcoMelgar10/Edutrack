package com.edutrack.desktop.ui.notes;

import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs the FX toolkit (the editor is a JavaFX control), started once for the whole class. */
class NoteEditorTest {

    private static final boolean MAC = System.getProperty("os.name", "").toLowerCase().contains("mac");

    @BeforeAll
    static void startToolkit() throws InterruptedException {
        CountDownLatch started = new CountDownLatch(1);
        try {
            Platform.startup(started::countDown);
        } catch (IllegalStateException alreadyRunning) {
            started.countDown();
        }
        started.await();
    }

    private static <T> T onFx(Callable<T> action) throws Exception {
        CompletableFuture<T> result = new CompletableFuture<>();
        Platform.runLater(() -> {
            try {
                result.complete(action.call());
            } catch (Throwable failure) {
                result.completeExceptionally(failure);
            }
        });
        return result.get(10, TimeUnit.SECONDS);
    }

    /** Lets the editor's own "style it on the next pulse" work run. */
    private static void settle() throws Exception {
        onFx(() -> null);
        onFx(() -> null);
    }

    private static NoteEditor editor(String markdown) throws Exception {
        return onFx(() -> {
            NoteEditor editor = new NoteEditor();
            editor.markdownProperty().set(markdown);
            return editor;
        });
    }

    private static void key(NoteEditor editor, KeyCode code, boolean shortcut, boolean shift) throws Exception {
        onFx(() -> {
            editor.area().fireEvent(new KeyEvent(KeyEvent.KEY_PRESSED, "", "", code, shift, shortcut && !MAC, false,
                    shortcut && MAC));
            return null;
        });
        settle();
    }

    @Test
    void aNoteComesBackExactlyAsItWentIn() throws Exception {
        List<String> notes = List.of("", "plain", "# Title\n\nSome **bold** and *it* text.\n",
                "- [ ] one\n- [x] two\n    - nested\n1. a\n2. b\n", "| A | B |\n|---|---|\n| 1 | 2 |\n",
                "```java\nint **a** = 1;\n```\n\n---\n> quote\n", "🎧 canción ñ — “comillas”\n\ttab", "unclosed **bold and `code");
        for (String note : notes) {
            NoteEditor editor = editor(note);
            settle();

            assertThat(onFx(() -> editor.area().getText())).isEqualTo(note);
            assertThat(editor.markdownProperty().get()).isEqualTo(note);
        }
    }

    @Test
    void loadingANoteFromOutsideIsNotAnUndoStep() throws Exception {
        NoteEditor editor = editor("first");
        settle();

        assertThat(onFx(() -> editor.area().getUndoManager().isUndoAvailable())).isFalse();
        onFx(() -> {
            editor.area().replaceText(5, 5, "!");
            return null;
        });
        assertThat(onFx(() -> editor.area().getUndoManager().isUndoAvailable())).isTrue();
        assertThat(editor.markdownProperty().get()).isEqualTo("first!");
    }

    @Test
    void stylingNeverBecomesAnUndoStep() throws Exception {
        NoteEditor editor = editor("# Title");
        settle();

        assertThat(onFx(() -> editor.area().getUndoManager().isUndoAvailable())).isFalse();
    }

    @Test
    void settingTheNoteAgainReplacesTheText() throws Exception {
        NoteEditor editor = editor("old");
        onFx(() -> {
            editor.markdownProperty().set("new text");
            return null;
        });

        assertThat(onFx(() -> editor.area().getText())).isEqualTo("new text");
    }

    @Test
    void aHeadingLooksLikeOneAndItsMarkerCollapsesUntilTheLineIsEdited() throws Exception {
        NoteEditor editor = editor("# Title\nbody");
        settle();

        assertThat(onFx(() -> editor.area().getParagraph(0).getParagraphStyle())).contains("md-p-h1");
        assertThat(onFx(() -> editor.area().getStyleOfChar(0))).contains("md-mark", "md-mark-hidden");
        assertThat(onFx(() -> editor.area().getStyleOfChar(3))).contains("md-h1-text");

        onFx(() -> {
            editor.showMarkersOn(Set.of(0));
            return null;
        });
        settle();

        assertThat(onFx(() -> editor.area().getStyleOfChar(0))).contains("md-mark-shown")
                .doesNotContain("md-mark-hidden");
    }

    @Test
    void ctrlBBoldsTheSelectionAndAgainUnbolds() throws Exception {
        NoteEditor editor = editor("one two");
        onFx(() -> {
            editor.area().selectRange(4, 7);
            return null;
        });

        key(editor, KeyCode.B, true, false);
        assertThat(editor.markdownProperty().get()).isEqualTo("one **two**");

        key(editor, KeyCode.B, true, false);
        assertThat(editor.markdownProperty().get()).isEqualTo("one two");
    }

    @Test
    void underlineHighlightAndStrikeUseTheirMarkers() throws Exception {
        NoteEditor editor = editor("word");
        onFx(() -> {
            editor.area().selectRange(0, 4);
            return null;
        });

        key(editor, KeyCode.U, true, false);
        assertThat(editor.markdownProperty().get()).isEqualTo("++word++");
        key(editor, KeyCode.U, true, false);
        key(editor, KeyCode.H, true, true);
        assertThat(editor.markdownProperty().get()).isEqualTo("==word==");
        key(editor, KeyCode.H, true, true);
        key(editor, KeyCode.S, true, true);
        assertThat(editor.markdownProperty().get()).isEqualTo("~~word~~");
    }

    @Test
    void enterContinuesAListAndTabNestsTheNewItem() throws Exception {
        NoteEditor editor = editor("- one");
        onFx(() -> {
            editor.area().moveTo(5);
            return null;
        });

        key(editor, KeyCode.ENTER, false, false);
        assertThat(editor.markdownProperty().get()).isEqualTo("- one\n- ");

        key(editor, KeyCode.TAB, false, false);
        assertThat(editor.markdownProperty().get()).isEqualTo("- one\n    - ");
    }

    @Test
    void ctrlKOnlyActsOnASelectionSoGlobalSearchStaysAvailable() throws Exception {
        NoteEditor editor = editor("some text");
        KeyEvent[] sent = new KeyEvent[1];
        onFx(() -> {
            editor.area().moveTo(2);
            sent[0] = new KeyEvent(KeyEvent.KEY_PRESSED, "", "", KeyCode.K, false, !MAC, false, MAC);
            editor.area().fireEvent(sent[0]);
            return null;
        });

        assertThat(sent[0].isConsumed()).isFalse();
    }

    @Test
    void theToolbarFollowsTheCaret() throws Exception {
        NoteEditor editor = editor("a **bold** b");
        onFx(() -> {
            editor.area().moveTo(5);
            return null;
        });
        settle();

        boolean boldSelected = onFx(() -> editor.lookupAll(".note-tool").stream()
                .anyMatch(node -> node instanceof javafx.scene.control.ToggleButton toggle && toggle.isSelected()));

        assertThat(boldSelected).isTrue();
    }

    @Test
    void aReadOnlyEditorIgnoresTheTools() throws Exception {
        NoteEditor editor = editor("one two");
        onFx(() -> {
            editor.setEditable(false);
            editor.area().selectRange(0, 3);
            return null;
        });

        key(editor, KeyCode.B, true, false);

        assertThat(editor.markdownProperty().get()).isEqualTo("one two");
    }
}
