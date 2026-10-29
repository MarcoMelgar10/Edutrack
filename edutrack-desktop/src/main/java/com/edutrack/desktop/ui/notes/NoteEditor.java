package com.edutrack.desktop.ui.notes;

import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.markdown.HighlightExtension;
import com.edutrack.desktop.ui.notes.MarkdownEdits.Block;
import com.edutrack.desktop.ui.notes.MarkdownEdits.Edit;
import com.edutrack.desktop.ui.notes.MarkdownStyler.BlockInfo;
import com.edutrack.desktop.ui.notes.MarkdownStyler.Kind;
import com.edutrack.desktop.ui.notes.MarkdownStyler.Segment;
import com.edutrack.desktop.ui.notes.MarkdownStyler.Styling;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.StyleClassedTextArea;
import org.fxmisc.richtext.model.PlainTextChange;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.fxmisc.richtext.util.UndoUtils;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One surface for writing notes, like Notion: what you type is shown formatted as you type it, with no second
 * pane for a preview. Under the hood the note stays plain Markdown text (so search, backup and export keep
 * working and nothing is ever lost), and the editor only styles it: titles are big, bold is bold, and the
 * syntax characters ({@code #}, {@code **}, {@code - }…) are collapsed on every line except the one the caret
 * is on, where they show so the text can be edited exactly.
 *
 * <p>Tools: a toolbar (block type, bold, italic, underline, strike-through, highlight, code, link, insert),
 * a {@code /} menu to turn a line into any block, keyboard shortcuts, lists that continue on Enter and nest
 * with Tab, clickable task boxes, and "make a flashcard from the selection".
 */
public final class NoteEditor extends VBox {

    private static final Pattern URL = Pattern.compile("(?i)https?://\\S+");
    private static final Pattern SLASH = Pattern.compile("^\\s*/([\\p{L}\\p{N}]*)$");
    private static final List<String> INLINE = List.of("**", "*", "++", "~~", "==", "`");

    private final StyleClassedTextArea area = new StyleClassedTextArea();
    private final StringProperty markdown = new SimpleStringProperty("");
    private final BooleanProperty editorFocused = new SimpleBooleanProperty();
    private final Label prompt = new Label();
    private final HBox toolbar = new HBox(2);
    private final MenuButton blockMenu = new MenuButton();
    private final ToggleButton[] inlineButtons = new ToggleButton[INLINE.size()];
    private final MenuButton highlightMenu = new MenuButton();
    private final Button cardButton = new Button();
    private final SlashMenu slash = new SlashMenu();
    private final LinkPrompt linkPrompt = new LinkPrompt();

    private Styling styling = MarkdownStyler.style("");
    private Set<Integer> active = Set.of();
    private boolean updating;
    private Set<Integer> forcedActive;
    private String highlightColor = HighlightExtension.DEFAULT_COLOR;
    private boolean restylePending;
    private boolean activePending;
    private Consumer<String> onMakeCard;
    private Consumer<String> onLinkClick = url -> { };

    public NoteEditor() {
        getStyleClass().add("note-editor");
        buildToolbar();

        area.getStyleClass().add("note-area");
        area.setWrapText(true);
        area.setParagraphGraphicFactory(this::gutter);
        // Only typing is undoable: the styling the editor applies must never be an undo step of its own.
        area.setUndoManager(UndoUtils.plainTextUndoManager(area));

        VirtualizedScrollPane<StyleClassedTextArea> scroller = new VirtualizedScrollPane<>(area);
        prompt.getStyleClass().add("note-prompt");
        prompt.setMouseTransparent(true);
        prompt.visibleProperty().bind(Bindings.createBooleanBinding(() -> area.getText().isEmpty(), area.textProperty()));
        StackPane stack = new StackPane(scroller, prompt);
        StackPane.setAlignment(prompt, Pos.TOP_LEFT);
        VBox.setVgrow(stack, Priority.ALWAYS);
        getChildren().addAll(toolbar, stack);

        area.textProperty().subscribe(text -> {
            if (!updating) {
                markdown.set(text);
            }
            scheduleRestyle();
        });
        markdown.subscribe(text -> {
            if (!updating && !text.equals(area.getText())) {
                load(text);
            }
        });
        area.plainTextChanges().subscribe(this::onChange);
        area.caretPositionProperty().subscribe(position -> scheduleActive());
        area.selectionProperty().subscribe(selection -> scheduleActive());
        area.focusedProperty().subscribe(focused -> {
            editorFocused.set(focused);
            if (!focused) {
                slash.hide();
            }
            scheduleActive();
        });
        area.addEventFilter(KeyEvent.KEY_PRESSED, this::onKey);
        area.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onClick);
    }

    // ---------- public API ----------

    /** The note as Markdown: two-way. Setting it from outside replaces the document without counting as an edit. */
    public StringProperty markdownProperty() {
        return markdown;
    }

    public void setPromptText(String text) {
        prompt.setText(text);
    }

    public void setEditable(boolean editable) {
        area.setEditable(editable);
        toolbar.setDisable(!editable);
    }

    /** Receives the selected text when the learner makes a flashcard from it; without a handler there is no button. */
    public void setOnMakeCard(Consumer<String> handler) {
        this.onMakeCard = handler;
        cardButton.setVisible(handler != null);
        cardButton.setManaged(handler != null);
    }

    /** Receives the address of a link that was Ctrl+clicked. */
    public void setOnLinkClick(Consumer<String> handler) {
        this.onLinkClick = handler == null ? url -> { } : handler;
    }

    public void focusEditor() {
        area.requestFocus();
    }

    /** True while the writing area has the keyboard focus. */
    public ReadOnlyBooleanProperty editorFocusedProperty() {
        return editorFocused;
    }

    public String selectedText() {
        return area.getSelectedText();
    }

    /** The text area itself, for tests and for hosts that need its caret. */
    StyleClassedTextArea area() {
        return area;
    }

    Styling styling() {
        return styling;
    }

    /** For tests and renders without a window: shows the syntax of these lines as if the caret were on them. */
    void showMarkersOn(Set<Integer> lines) {
        forcedActive = Set.copyOf(lines);
        restyle();
    }

    // ---------- loading and styling ----------

    private void load(String text) {
        updating = true;
        try {
            area.replaceText(text.replace("\r\n", "\n"));
            area.getUndoManager().forgetHistory();
            area.moveTo(0);
        } finally {
            updating = false;
        }
        restyle();
    }

    private void scheduleRestyle() {
        if (!restylePending) {
            restylePending = true;
            Platform.runLater(() -> {
                restylePending = false;
                restyle();
            });
        }
    }

    private void scheduleActive() {
        if (!activePending) {
            activePending = true;
            Platform.runLater(() -> {
                activePending = false;
                updateActive();
            });
        }
    }

    /** Parses the whole note again and applies every style; called after the text changed. */
    private void restyle() {
        styling = MarkdownStyler.style(area.getText());
        active = activeLines();
        int length = styling.text().length();
        if (length > 0) {
            area.setStyleSpans(0, spans(0, length));
        }
        for (int line = 0; line < styling.lineCount() && line < area.getParagraphs().size(); line++) {
            area.setParagraphStyle(line, paragraphStyle(line));
        }
        refreshToolbar();
    }

    /** The caret moved: only the lines that became (or stopped being) the active ones are touched. */
    private void updateActive() {
        Set<Integer> now = activeLines();
        if (!now.equals(active)) {
            Set<Integer> changed = new HashSet<>(now);
            changed.addAll(active);
            Set<Integer> before = active;
            active = now;
            for (int line : changed) {
                if (before.contains(line) != now.contains(line) && line < styling.lineCount()
                        && line < area.getParagraphs().size()) {
                    int start = styling.lineStarts()[line];
                    int end = styling.lineEnd(line);
                    if (end > start) {
                        area.setStyleSpans(line, 0, spans(start, end));
                    }
                    area.setParagraphStyle(line, paragraphStyle(line));
                }
            }
        }
        refreshToolbar();
    }

    private StyleSpans<Collection<String>> spans(int from, int to) {
        StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
        for (Segment segment : styling.segments(from, to, active)) {
            builder.add(segment.classes(), segment.length());
        }
        return builder.create();
    }

    /** The lines the caret or the selection touches; none while the editor is not in use, so the note reads clean. */
    private Set<Integer> activeLines() {
        if (forcedActive != null) {
            return forcedActive;
        }
        return area.isFocused() ? lines(area.getSelection().getStart(), area.getSelection().getEnd()) : Set.of();
    }

    private Set<Integer> lines(int start, int end) {
        Set<Integer> lines = new HashSet<>();
        int first = styling.lineOf(Math.min(start, styling.text().length()));
        int last = styling.lineOf(Math.min(end, styling.text().length()));
        for (int line = first; line <= last; line++) {
            lines.add(line);
        }
        return lines;
    }

    /** Classes of a line's box. The last one changes whenever the gutter must be redrawn. */
    private List<String> paragraphStyle(int line) {
        BlockInfo info = styling.block(line);
        List<String> classes = new ArrayList<>();
        switch (info.kind()) {
            case H1 -> classes.add("md-p-h1");
            case H2 -> classes.add("md-p-h2");
            case H3 -> classes.add("md-p-h3");
            case CODE, CODE_FENCE -> classes.add("md-p-code");
            case RULE -> classes.add("md-p-rule");
            case TABLE_HEAD, TABLE_ROW, TABLE_RULE -> classes.add("md-p-table");
            default -> { }
        }
        if (info.quote()) {
            classes.add("md-p-quote");
        }
        classes.add("g-" + info.kind() + "-" + info.level() + "-" + info.ordinal() + "-" + info.checked() + "-"
                + active.contains(line));
        return classes;
    }

    // ---------- the gutter: bullets, numbers and task boxes ----------

    private Node gutter(int line) {
        if (styling == null || line >= styling.lineCount()) {
            return null;
        }
        BlockInfo info = styling.block(line);
        if (!info.isListItem()) {
            return null;
        }
        double width = 24 + (info.level() - 1) * 24;
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setMinWidth(width);
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        box.setPadding(new Insets(0, 8, 0, 0));
        box.getStyleClass().add("note-gutter");
        if (active.contains(line)) {
            return box; // the raw marker is showing on this line: only keep the indentation
        }
        switch (info.kind()) {
            case BULLET -> {
                Label bullet = new Label(info.level() == 1 ? "•" : info.level() == 2 ? "◦" : "▪");
                bullet.getStyleClass().add("note-bullet");
                box.getChildren().add(bullet);
            }
            case NUMBERED -> {
                Label number = new Label(info.ordinal() + ".");
                number.getStyleClass().add("note-number");
                box.getChildren().add(number);
            }
            default -> {
                StackPane check = new StackPane();
                check.getStyleClass().add("md-task");
                if (info.checked()) {
                    check.getStyleClass().add("md-task-done");
                    FontIcon icon = new FontIcon(Feather.CHECK);
                    icon.getStyleClass().add("md-task-check");
                    check.getChildren().add(icon);
                }
                check.setOnMouseClicked(click -> {
                    apply(MarkdownEdits.toggleTask(area.getText(), line));
                    click.consume();
                });
                box.getChildren().add(check);
            }
        }
        return box;
    }

    // ---------- applying edits ----------

    private void apply(Edit edit) {
        if (edit == null || !area.isEditable()) {
            return;
        }
        area.replaceText(edit.from(), edit.to(), edit.replacement());
        if (edit.selStart() == edit.selEnd()) {
            area.moveTo(edit.selStart());
        } else {
            area.selectRange(edit.selStart(), edit.selEnd());
        }
        area.requestFollowCaret();
        area.requestFocus();
    }

    private int start() {
        return area.getSelection().getStart();
    }

    private int end() {
        return area.getSelection().getEnd();
    }

    private void toggle(String marker) {
        if ("==".equals(marker)) {
            apply(MarkdownEdits.toggleHighlight(area.getText(), start(), end(), highlightColor));
            return;
        }
        apply(MarkdownEdits.toggleInline(area.getText(), start(), end(), marker));
    }

    /** Highlights the selection in {@code color} (or takes the highlight off for {@code null}); the button remembers the colour. */
    private void highlight(String color) {
        if (color != null) {
            highlightColor = color;
        }
        apply(MarkdownEdits.applyHighlight(area.getText(), start(), end(), color));
        refreshToolbar();
    }

    private void block(Block block) {
        apply(MarkdownEdits.setBlock(area.getText(), start(), end(), block));
    }

    // ---------- keyboard ----------

    private void onKey(KeyEvent event) {
        if (slash.isShowing() && slash.handle(event)) {
            event.consume();
            return;
        }
        if (!area.isEditable()) {
            return;
        }
        KeyCode code = event.getCode();
        boolean shortcut = event.isShortcutDown();
        if (shortcut && !event.isAltDown()) {
            boolean handled = true;
            switch (code) {
                case B -> toggle("**");
                case I -> toggle("*");
                case U -> toggle("++");
                case E -> toggle("`");
                case S -> {
                    if (event.isShiftDown()) {
                        toggle("~~");
                    } else {
                        handled = false;
                    }
                }
                case H -> {
                    if (event.isShiftDown()) {
                        toggle("==");
                    } else {
                        handled = false;
                    }
                }
                case K -> {
                    if (event.isShiftDown()) {
                        makeCard();
                    } else if (area.getSelection().getLength() > 0) {
                        askForLink();
                    } else {
                        handled = false; // Ctrl+K opens the global search when no text is selected
                    }
                }
                case DIGIT7 -> {
                    if (event.isShiftDown()) {
                        block(Block.NUMBERED);
                    } else {
                        handled = false;
                    }
                }
                case DIGIT8 -> {
                    if (event.isShiftDown()) {
                        block(Block.BULLET);
                    } else {
                        handled = false;
                    }
                }
                case DIGIT9 -> {
                    if (event.isShiftDown()) {
                        block(Block.TASK);
                    } else {
                        handled = false;
                    }
                }
                case V -> handled = pasteLink();
                default -> handled = false;
            }
            if (handled) {
                event.consume();
            }
            return;
        }
        if (shortcut && event.isAltDown()) {
            Block block = switch (code) {
                case DIGIT1 -> Block.H1;
                case DIGIT2 -> Block.H2;
                case DIGIT3 -> Block.H3;
                case DIGIT0 -> Block.TEXT;
                default -> null;
            };
            if (block != null) {
                block(block);
                event.consume();
            }
            return;
        }
        if (event.isControlDown() || event.isAltDown() || event.isMetaDown()) {
            return;
        }
        boolean collapsed = area.getSelection().getLength() == 0;
        switch (code) {
            case ENTER -> {
                if (collapsed && !event.isShiftDown()) {
                    Edit edit = MarkdownEdits.onEnter(area.getText(), area.getCaretPosition());
                    if (edit != null) {
                        apply(edit);
                        event.consume();
                    }
                }
            }
            case TAB -> {
                Edit edit = MarkdownEdits.onTab(area.getText(), start(), end(), event.isShiftDown());
                if (edit != null) {
                    apply(edit);
                    event.consume();
                } else if (event.isShiftDown()) {
                    event.consume();
                }
            }
            case BACK_SPACE -> {
                if (collapsed) {
                    Edit edit = MarkdownEdits.onBackspace(area.getText(), area.getCaretPosition());
                    if (edit != null) {
                        apply(edit);
                        event.consume();
                    }
                }
            }
            default -> { }
        }
    }

    /** Pasting an address over selected text makes it a link; anything else pastes as usual. */
    private boolean pasteLink() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (area.getSelection().getLength() > 0 && clipboard.hasString()) {
            String pasted = clipboard.getString().strip();
            if (URL.matcher(pasted).matches()) {
                apply(MarkdownEdits.makeLink(area.getText(), start(), end(), pasted));
                return true;
            }
        }
        return false;
    }

    private void makeCard() {
        if (onMakeCard == null) {
            return;
        }
        String selected = area.getSelectedText();
        onMakeCard.accept(selected.isBlank() ? lineAtCaret() : selected.strip());
    }

    private String lineAtCaret() {
        int line = styling.lineOf(area.getCaretPosition());
        String text = styling.text().substring(styling.lineStarts()[line], styling.lineEnd(line));
        return MarkdownEdits.kindOf(text) == Block.TEXT ? text.strip() : text.replaceFirst("^\\s*(#{1,6}|[-*+>]|\\d+[.)])\\s+(\\[[ xX]\\]\\s+)?", "");
    }

    private void askForLink() {
        int from = start();
        int to = end();
        linkPrompt.show(url -> apply(MarkdownEdits.makeLink(area.getText(), from, to, url)));
    }

    // ---------- mouse ----------

    /** Ctrl+click on a link opens it; a plain click only places the caret. */
    private void onClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || !event.isShortcutDown()) {
            return;
        }
        int offset = area.hit(event.getX(), event.getY()).getInsertionIndex();
        for (MarkdownStyler.LinkRange link : styling.links()) {
            if (offset >= link.start() && offset <= link.end()) {
                onLinkClick.accept(link.url());
                event.consume();
                return;
            }
        }
    }

    // ---------- the / menu ----------

    private void onChange(PlainTextChange change) {
        if (updating) {
            return;
        }
        String line = lineBeforeCaretAfter(change);
        if (slash.isShowing()) {
            Matcher matcher = SLASH.matcher(line);
            if (matcher.matches()) {
                slash.filter(matcher.group(1));
            } else {
                slash.hide();
            }
        } else if (change.getInserted().equals("/") && change.getRemoved().isEmpty() && SLASH.matcher(line).matches()) {
            Platform.runLater(() -> slash.open(area.getCaretBounds().orElse(null)));
        }
    }

    private String lineBeforeCaretAfter(PlainTextChange change) {
        int caret = change.getPosition() + change.getInserted().length();
        String text = area.getText();
        caret = Math.min(caret, text.length());
        int lineStart = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        if (caret < lineStart) {
            lineStart = 0;
        }
        return text.substring(lineStart, caret);
    }

    /** Removes what was typed after the slash and runs the chosen block. */
    private void chooseSlash(Runnable action) {
        int caret = area.getCaretPosition();
        String text = area.getText();
        int lineStart = text.lastIndexOf('\n', Math.max(0, caret - 1)) + 1;
        String before = text.substring(lineStart, caret);
        int slashAt = lineStart + before.indexOf('/');
        area.replaceText(slashAt, caret, "");
        area.moveTo(slashAt);
        action.run();
    }

    private final class SlashMenu {

        private final Popup popup = new Popup();
        private final ListView<Entry> list = new ListView<>();
        private final List<Entry> all = new ArrayList<>();

        private record Entry(String label, String keywords, Runnable action) {
            @Override
            public String toString() {
                return label;
            }
        }

        SlashMenu() {
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);
            list.getStyleClass().add("slash-menu");
            list.setPrefWidth(260);
            list.setFixedCellSize(32);
            list.setOnMouseClicked(click -> choose());
            StackPane wrapper = new StackPane(list);
            wrapper.getStyleClass().add("root"); // the popup is its own window: the theme tokens hang on .root
            popup.getContent().add(wrapper);
        }

        boolean isShowing() {
            return popup.isShowing();
        }

        void open(Bounds caret) {
            all.clear();
            add("note.block.text", "text paragraph plain texto parrafo", () -> block(Block.TEXT));
            add("note.block.h1", "heading title h1 titulo encabezado", () -> block(Block.H1));
            add("note.block.h2", "heading subtitle h2 subtitulo encabezado", () -> block(Block.H2));
            add("note.block.h3", "heading h3 encabezado", () -> block(Block.H3));
            add("note.block.bullet", "bullet list unordered lista viñetas", () -> block(Block.BULLET));
            add("note.block.numbered", "numbered list ordered lista numerada", () -> block(Block.NUMBERED));
            add("note.block.task", "todo task checkbox tarea lista", () -> block(Block.TASK));
            add("note.block.quote", "quote cita", () -> block(Block.QUOTE));
            add("note.block.code", "code snippet codigo", () -> apply(MarkdownEdits.insertCodeBlock(area.getText(),
                    area.getCaretPosition())));
            for (String color : HighlightExtension.COLORS) {
                add("note.highlight." + color, "highlight mark color colour resaltar marcar color " + color,
                        () -> highlight(color));
            }
            add("note.insert.divider", "divider rule line separador linea", () -> apply(MarkdownEdits.insertRule(
                    area.getText(), area.getCaretPosition())));
            add("note.insert.table", "table tabla", () -> apply(MarkdownEdits.insertTable(area.getText(),
                    area.getCaretPosition(), 3, 3)));
            add("note.insert.today", "date today fecha hoy", () -> apply(MarkdownEdits.insertText(area.getText(),
                    area.getCaretPosition(), area.getCaretPosition(), todayText())));
            list.getItems().setAll(all);
            list.getSelectionModel().selectFirst();
            if (caret != null) {
                popup.getScene().getStylesheets().setAll(area.getScene().getStylesheets());
                popup.show(area, caret.getMinX(), caret.getMaxY() + 4);
            }
        }

        private void add(String key, String keywords, Runnable action) {
            all.add(new Entry(I18n.get(key), (I18n.get(key) + " " + keywords).toLowerCase(Locale.ROOT), action));
        }

        void filter(String query) {
            String needle = query.toLowerCase(Locale.ROOT);
            List<Entry> matching = all.stream().filter(entry -> entry.keywords().contains(needle)).toList();
            if (matching.isEmpty()) {
                hide();
                return;
            }
            list.getItems().setAll(matching);
            list.setPrefHeight(Math.min(matching.size(), 8) * 32 + 4);
            list.getSelectionModel().selectFirst();
        }

        void hide() {
            popup.hide();
        }

        /** Arrow keys, Enter and Escape drive the menu while it is open; returns true when it used the key. */
        boolean handle(KeyEvent event) {
            switch (event.getCode()) {
                case DOWN -> list.getSelectionModel().selectNext();
                case UP -> list.getSelectionModel().selectPrevious();
                case ENTER, TAB -> choose();
                case ESCAPE -> hide();
                default -> {
                    return false;
                }
            }
            return true;
        }

        private void choose() {
            Entry entry = list.getSelectionModel().getSelectedItem();
            hide();
            if (entry != null) {
                chooseSlash(entry.action());
            }
        }
    }

    private static String todayText() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern(I18n.get("format.date"), I18n.locale()));
    }

    // ---------- the link popup ----------

    private final class LinkPrompt {

        private final Popup popup = new Popup();
        private final TextField field = new TextField();
        private Consumer<String> onUrl = url -> { };

        LinkPrompt() {
            popup.setAutoHide(true);
            popup.setHideOnEscape(true);
            field.getStyleClass().add("note-link-field");
            field.setPrefWidth(320);
            field.setPromptText(I18n.get("note.link.prompt"));
            field.setOnAction(action -> {
                String url = field.getText().strip();
                popup.hide();
                if (!url.isEmpty()) {
                    onUrl.accept(url);
                }
            });
            HBox box = new HBox(field);
            box.getStyleClass().addAll("note-link-popup", "root");
            popup.getContent().add(box);
        }

        void show(Consumer<String> handler) {
            onUrl = handler;
            field.clear();
            Bounds bounds = area.getCaretBounds().orElse(null);
            if (bounds != null) {
                popup.getScene().getStylesheets().setAll(area.getScene().getStylesheets());
                popup.show(area, bounds.getMinX(), bounds.getMaxY() + 6);
                field.requestFocus();
            }
        }
    }

    // ---------- the toolbar ----------

    private void buildToolbar() {
        toolbar.getStyleClass().add("note-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);

        blockMenu.getStyleClass().add("note-block-menu");
        for (Object[] entry : new Object[][]{{"note.block.text", Block.TEXT}, {"note.block.h1", Block.H1},
                {"note.block.h2", Block.H2}, {"note.block.h3", Block.H3}, {"note.block.bullet", Block.BULLET},
                {"note.block.numbered", Block.NUMBERED}, {"note.block.task", Block.TASK},
                {"note.block.quote", Block.QUOTE}, {"note.block.code", Block.CODE}}) {
            MenuItem item = new MenuItem(I18n.get((String) entry[0]));
            Block block = (Block) entry[1];
            item.setOnAction(action -> block(block));
            blockMenu.getItems().add(item);
        }
        blockMenu.setText(I18n.get("note.block.text"));
        toolbar.getChildren().addAll(blockMenu, separator());

        String[] tips = {"note.bold", "note.italic", "note.underline", "note.strike", "note.highlight", "note.code"};
        for (int index = 0; index < INLINE.size(); index++) {
            ToggleButton button = new ToggleButton();
            button.getStyleClass().add("note-tool");
            button.setGraphic(inlineGraphic(index));
            button.setTooltip(new Tooltip(I18n.get(tips[index])));
            button.setAccessibleText(I18n.get(tips[index]));
            button.setFocusTraversable(false);
            String marker = INLINE.get(index);
            button.setOnAction(action -> {
                toggle(marker);
                refreshToolbar();
            });
            inlineButtons[index] = button;
            toolbar.getChildren().add(button);
            if ("==".equals(marker)) {
                buildHighlightMenu();
            }
        }
        Button link = tool(new FontIcon(Feather.LINK), I18n.get("note.link"), this::askForLink);
        MenuButton insert = new MenuButton();
        insert.setGraphic(new FontIcon(Feather.PLUS));
        insert.getStyleClass().add("note-tool");
        insert.setTooltip(new Tooltip(I18n.get("note.insert")));
        insert.setFocusTraversable(false);
        insert.getItems().addAll(
                item("note.insert.divider", () -> apply(MarkdownEdits.insertRule(area.getText(), area.getCaretPosition()))),
                item("note.insert.table", () -> apply(MarkdownEdits.insertTable(area.getText(), area.getCaretPosition(), 3, 3))),
                item("note.block.code", () -> apply(MarkdownEdits.insertCodeBlock(area.getText(), area.getCaretPosition()))),
                item("note.insert.today", () -> apply(MarkdownEdits.insertText(area.getText(), area.getCaretPosition(),
                        area.getCaretPosition(), todayText()))));
        toolbar.getChildren().addAll(separator(), link, insert);

        cardButton.setGraphic(new FontIcon(Feather.LAYERS));
        cardButton.getStyleClass().add("note-tool");
        cardButton.setTooltip(new Tooltip(I18n.get("note.makeCard")));
        cardButton.setAccessibleText(I18n.get("note.makeCard"));
        cardButton.setFocusTraversable(false);
        cardButton.setOnAction(action -> makeCard());
        cardButton.setVisible(false);
        cardButton.setManaged(false);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        toolbar.getChildren().addAll(spacer, cardButton);
    }

    /** The arrow next to the highlight button: one swatch per colour and "remove highlight". */
    private void buildHighlightMenu() {
        highlightMenu.setGraphic(new FontIcon(Feather.CHEVRON_DOWN));
        highlightMenu.getStyleClass().addAll("note-tool", "note-highlight-menu");
        highlightMenu.setTooltip(new Tooltip(I18n.get("note.highlight.colors")));
        highlightMenu.setAccessibleText(I18n.get("note.highlight.colors"));
        highlightMenu.setFocusTraversable(false);

        HBox swatches = new HBox(6);
        swatches.getStyleClass().add("hl-swatches");
        for (String color : HighlightExtension.COLORS) {
            Button swatch = new Button();
            swatch.getStyleClass().addAll("hl-swatch", "hl-swatch-" + color);
            swatch.setFocusTraversable(false);
            swatch.setTooltip(new Tooltip(I18n.get("note.highlight." + color)));
            swatch.setAccessibleText(I18n.get("note.highlight." + color));
            swatch.setOnAction(click -> {
                highlightMenu.hide();
                highlight(color);
            });
            swatches.getChildren().add(swatch);
        }
        CustomMenuItem colors = new CustomMenuItem(swatches, false);
        highlightMenu.getItems().addAll(colors, new SeparatorMenuItem(),
                item("note.highlight.remove", () -> highlight(null)));
        toolbar.getChildren().add(highlightMenu);
    }

    private MenuItem item(String key, Runnable action) {
        MenuItem item = new MenuItem(I18n.get(key));
        item.setOnAction(click -> action.run());
        return item;
    }

    private Button tool(Node graphic, String tip, Runnable action) {
        Button button = new Button();
        button.setGraphic(graphic);
        button.getStyleClass().add("note-tool");
        button.setTooltip(new Tooltip(tip));
        button.setAccessibleText(tip);
        button.setFocusTraversable(false);
        button.setOnAction(click -> action.run());
        return button;
    }

    private static Node inlineGraphic(int index) {
        return switch (index) {
            case 0 -> new FontIcon(Feather.BOLD);
            case 1 -> new FontIcon(Feather.ITALIC);
            case 2 -> new FontIcon(Feather.UNDERLINE);
            case 3 -> letter("S", "note-glyph-strike");
            case 4 -> letter("H", "note-glyph-highlight");
            default -> new FontIcon(Feather.CODE);
        };
    }

    private static Label letter(String text, String styleClass) {
        Label label = new Label(text);
        label.getStyleClass().add(styleClass);
        return label;
    }

    private static Separator separator() {
        Separator separator = new Separator(javafx.geometry.Orientation.VERTICAL);
        separator.getStyleClass().add("note-separator");
        return separator;
    }

    /** Shows what is on at the caret: which inline styles surround it and what kind of line it is on. */
    private void refreshToolbar() {
        if (styling == null) {
            return;
        }
        String text = area.getText();
        for (int index = 0; index < INLINE.size(); index++) {
            String marker = INLINE.get(index);
            inlineButtons[index].setSelected("==".equals(marker)
                    ? MarkdownEdits.highlightColor(text, start(), end()) != null
                    : MarkdownEdits.isWrapped(text, start(), end(), marker));
        }
        int line = Math.min(styling.lineOf(area.getCaretPosition()), styling.lineCount() - 1);
        String current = text.substring(styling.lineStarts()[line], styling.lineEnd(line));
        Kind kind = styling.block(line).kind();
        String key = switch (kind) {
            case CODE, CODE_FENCE -> "note.block.code";
            default -> switch (MarkdownEdits.kindOf(current)) {
                case H1 -> "note.block.h1";
                case H2 -> "note.block.h2";
                case H3 -> "note.block.h3";
                case BULLET -> "note.block.bullet";
                case NUMBERED -> "note.block.numbered";
                case TASK -> "note.block.task";
                case QUOTE -> "note.block.quote";
                default -> "note.block.text";
            };
        };
        blockMenu.setText(I18n.get(key));
    }
}
