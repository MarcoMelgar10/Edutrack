package com.edutrack.desktop.ui.notes;

import com.edutrack.desktop.ui.markdown.HighlightExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.Ins;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.Code;
import org.commonmark.node.Delimited;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out how a note, which is stored as Markdown text, should look while it is being edited: which
 * lines are headings, list items, tasks, quotes, code or table rows, which stretches of text are bold,
 * italic, underlined, highlighted, code or links, and which characters are only syntax markers ({@code **},
 * {@code #}, {@code - }…). The editor shows the markers of the line being edited and collapses all the others,
 * so the note reads as formatted text without a second preview pane.
 *
 * <p>This class is pure Java: the editor turns its result into JavaFX styles. It never changes the text.
 * The same CommonMark parser (with tables, strike-through, task lists, {@code ++underline++} and
 * {@code ==highlight==}) that renders read-only notes is used, so both views agree.
 */
public final class MarkdownStyler {

    /** What a line is. */
    public enum Kind { TEXT, H1, H2, H3, BULLET, NUMBERED, TASK, CODE_FENCE, CODE, TABLE_HEAD, TABLE_RULE, TABLE_ROW, RULE }

    /**
     * One line of the note. {@code level} is the nesting depth of a list item (1 for the top level);
     * {@code ordinal} the number of a numbered item; {@code quote} whether the line is inside a block quote.
     */
    public record BlockInfo(Kind kind, int level, int ordinal, boolean checked, boolean quote) {

        static final BlockInfo TEXT = new BlockInfo(Kind.TEXT, 0, 0, false, false);

        public boolean isListItem() {
            return kind == Kind.BULLET || kind == Kind.NUMBERED || kind == Kind.TASK;
        }
    }

    /** A stretch of text with a style class ({@code md-strong}, {@code md-link}…). */
    public record Styled(int start, int end, String styleClass) {
    }

    /** A stretch of syntax characters ({@code **}, {@code # }, {@code ](url)}…). */
    public record Mark(int start, int end) {
    }

    /** The text of a link and where it goes; the editor opens it on Ctrl+click. */
    public record LinkRange(int start, int end, String url) {
    }

    /** A run of text with the same classes, as the editor applies them. */
    public record Segment(int length, List<String> classes) {
    }

    /**
     * The result for one text.
     *
     * @param lineStarts offset of the first character of each line
     */
    public record Styling(String text, int[] lineStarts, List<BlockInfo> blocks, List<Styled> styled, List<Mark> marks,
                          List<LinkRange> links) {

        public int lineCount() {
            return lineStarts.length;
        }

        public int lineEnd(int line) {
            return line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
        }

        /** The line (paragraph) an offset belongs to. */
        public int lineOf(int offset) {
            int found = Arrays.binarySearch(lineStarts, offset);
            return found >= 0 ? found : -found - 2;
        }

        public BlockInfo block(int line) {
            return line >= 0 && line < blocks.size() ? blocks.get(line) : BlockInfo.TEXT;
        }

        /** The inline style classes (not syntax markers) in effect at an offset, e.g. {@code md-strong}. */
        public Set<String> classesAt(int offset) {
            Set<String> classes = new LinkedHashSet<>();
            for (Styled range : styled) {
                if (range.start() <= offset && offset < range.end()) {
                    classes.add(range.styleClass());
                }
            }
            return classes;
        }

        /**
         * The text between {@code from} and {@code to} cut into runs of equal style. Syntax markers get
         * {@code md-mark} plus {@code md-mark-shown} on lines in {@code active} (the lines being edited) and
         * {@code md-mark-hidden} on every other line.
         */
        public List<Segment> segments(int from, int to, Set<Integer> active) {
            int start = Math.max(0, from);
            int end = Math.min(text.length(), to);
            if (start >= end) {
                return List.of();
            }
            TreeMap<Integer, List<String[]>> events = new TreeMap<>();
            events.computeIfAbsent(start, key -> new ArrayList<>());
            events.computeIfAbsent(end, key -> new ArrayList<>());
            for (Styled range : styled) {
                if (range.end() > start && range.start() < end) {
                    add(events, Math.max(start, range.start()), "+", range.styleClass());
                    add(events, Math.min(end, range.end()), "-", range.styleClass());
                }
            }
            for (Mark mark : marks) {
                if (mark.end() > start && mark.start() < end) {
                    int from1 = Math.max(start, mark.start());
                    int to1 = Math.min(end, mark.end());
                    // A mark never crosses a line, so one state covers it; split it where lines begin.
                    int cursor = from1;
                    while (cursor < to1) {
                        int line = lineOf(cursor);
                        int stop = Math.min(to1, lineEnd(line) + 1);
                        String state = active.contains(line) ? "md-mark-shown" : "md-mark-hidden";
                        add(events, cursor, "+", "md-mark");
                        add(events, cursor, "+", state);
                        add(events, stop, "-", "md-mark");
                        add(events, stop, "-", state);
                        cursor = stop;
                    }
                }
            }
            List<Segment> segments = new ArrayList<>();
            java.util.Map<String, Integer> open = new java.util.LinkedHashMap<>();
            Integer previous = null;
            for (var entry : events.entrySet()) {
                int position = entry.getKey();
                if (previous != null && position > previous) {
                    List<String> classes = open.entrySet().stream().filter(each -> each.getValue() > 0)
                            .map(java.util.Map.Entry::getKey).toList();
                    if (!segments.isEmpty() && segments.getLast().classes().equals(classes)) {
                        Segment last = segments.removeLast();
                        segments.add(new Segment(last.length() + position - previous, classes));
                    } else {
                        segments.add(new Segment(position - previous, classes));
                    }
                }
                for (String[] event : entry.getValue()) {
                    open.merge(event[1], event[0].equals("+") ? 1 : -1, Integer::sum);
                }
                previous = position;
            }
            return segments;
        }

        private static void add(TreeMap<Integer, List<String[]>> events, int position, String sign, String styleClass) {
            events.computeIfAbsent(position, key -> new ArrayList<>()).add(new String[]{sign, styleClass});
        }
    }

    private static final Pattern LIST_MARKER = Pattern.compile("^(\\s*)([-*+]|\\d{1,9}[.)])(\\s+)(\\[[ xX]\\]\\s+)?");
    private static final Pattern QUOTE_MARKER = Pattern.compile("^(\\s{0,3}>\\s?)+");
    private static final Pattern HEADING_MARKER = Pattern.compile("^\\s{0,3}#{1,6}[ \\t]*");
    private static final Pattern HEADING_CLOSING = Pattern.compile("[ \\t]+#+[ \\t]*$");
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})");

    private static final Parser PARSER = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
                    InsExtension.create(), HighlightExtension.create()))
            .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .build();

    private MarkdownStyler() {
    }

    /** Styles {@code text}; carriage returns are not expected (the editor keeps lines separated by {@code \n}). */
    public static Styling style(String text) {
        String source = text == null ? "" : text;
        int[] lineStarts = lineStarts(source);
        List<BlockInfo> blocks = new ArrayList<>(Collections.nCopies(lineStarts.length, BlockInfo.TEXT));
        List<Styled> styled = new ArrayList<>();
        List<Mark> marks = new ArrayList<>();
        List<LinkRange> links = new ArrayList<>();
        if (!source.isBlank()) {
            try {
                Builder builder = new Builder(source, lineStarts, blocks, styled, marks, links);
                builder.walk(PARSER.parse(source), 0, false);
            } catch (RuntimeException unexpected) {
                // Malformed input must never break editing: fall back to plain text.
                return new Styling(source, lineStarts, new ArrayList<>(Collections.nCopies(lineStarts.length, BlockInfo.TEXT)),
                        List.of(), List.of(), List.of());
            }
        }
        return new Styling(source, lineStarts, blocks, styled, marks, links);
    }

    static int[] lineStarts(String text) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                starts.add(index + 1);
            }
        }
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }

    /** Collects the blocks and inline styles while walking the CommonMark tree. */
    private static final class Builder {

        private final String text;
        private final int[] lineStarts;
        private final List<BlockInfo> blocks;
        private final List<Styled> styled;
        private final List<Mark> marks;
        private final List<LinkRange> links;

        Builder(String text, int[] lineStarts, List<BlockInfo> blocks, List<Styled> styled, List<Mark> marks,
                List<LinkRange> links) {
            this.links = links;
            this.text = text;
            this.lineStarts = lineStarts;
            this.blocks = blocks;
            this.styled = styled;
            this.marks = marks;
        }

        void walk(Node parent, int listDepth, boolean inQuote) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
                block(node, listDepth, inQuote);
            }
        }

        private void block(Node node, int listDepth, boolean inQuote) {
            switch (node) {
                case Heading heading -> heading(heading, inQuote);
                case FencedCodeBlock fenced -> fenced(fenced, inQuote);
                case IndentedCodeBlock indented -> lines(indented, Kind.CODE, inQuote);
                case ThematicBreak rule -> {
                    int line = firstLine(rule);
                    if (line >= 0) {
                        set(line, new BlockInfo(Kind.RULE, 0, 0, false, inQuote));
                        mark(lineStarts[line], lineEnd(line));
                    }
                }
                case BlockQuote quote -> {
                    quoteMarkers(quote);
                    walk(quote, listDepth, true);
                }
                case ListItem item -> listItem(item, listDepth + 1, inQuote);
                case TableBlock table -> table(table, inQuote);
                default -> {
                    if (node.getFirstChild() != null && isContainer(node)) {
                        walk(node, listDepth, inQuote);
                    } else {
                        inlines(node);
                        flagQuote(node, inQuote);
                    }
                }
            }
        }

        private boolean isContainer(Node node) {
            return node instanceof org.commonmark.node.BulletList || node instanceof OrderedList
                    || node instanceof org.commonmark.node.Document;
        }

        // ---- blocks ----

        private void heading(Heading heading, boolean inQuote) {
            int line = firstLine(heading);
            if (line < 0) {
                return;
            }
            Kind kind = switch (Math.min(heading.getLevel(), 3)) {
                case 1 -> Kind.H1;
                case 2 -> Kind.H2;
                default -> Kind.H3;
            };
            set(line, new BlockInfo(kind, heading.getLevel(), 0, false, inQuote));
            String content = lineText(line);
            Matcher opening = HEADING_MARKER.matcher(content);
            if (opening.find() && opening.end() > 0) {
                mark(lineStarts[line], lineStarts[line] + opening.end());
                Matcher closing = HEADING_CLOSING.matcher(content);
                if (closing.find() && closing.start() >= opening.end()) {
                    mark(lineStarts[line] + closing.start(), lineStarts[line] + content.length());
                }
            } else if (lastLine(heading) > line) {
                // A heading underlined with === or --- : the underline is syntax.
                int underline = lastLine(heading);
                set(underline, new BlockInfo(kind, heading.getLevel(), 0, false, inQuote));
                mark(lineStarts[underline], lineEnd(underline));
            }
            inlines(heading);
            addStyled(styleStart(heading), styleEnd(heading), "md-h" + Math.min(heading.getLevel(), 3) + "-text");
        }

        private void fenced(FencedCodeBlock fenced, boolean inQuote) {
            int first = firstLine(fenced);
            int last = lastLine(fenced);
            if (first < 0) {
                return;
            }
            set(first, new BlockInfo(Kind.CODE_FENCE, 0, 0, false, inQuote));
            mark(lineStarts[first], lineEnd(first));
            boolean closed = last > first && FENCE.matcher(lineText(last)).find()
                    && lineText(last).strip().replaceAll("[`~]", "").isEmpty();
            for (int line = first + 1; line <= last; line++) {
                if (closed && line == last) {
                    set(line, new BlockInfo(Kind.CODE_FENCE, 0, 0, false, inQuote));
                    mark(lineStarts[line], lineEnd(line));
                } else {
                    set(line, new BlockInfo(Kind.CODE, 0, 0, false, inQuote));
                    addStyled(lineStarts[line], lineEnd(line), "md-code-block");
                }
            }
        }

        private void lines(Node node, Kind kind, boolean inQuote) {
            int first = firstLine(node);
            int last = lastLine(node);
            for (int line = Math.max(0, first); line >= 0 && line <= last; line++) {
                set(line, new BlockInfo(kind, 0, 0, false, inQuote));
                addStyled(lineStarts[line], lineEnd(line), "md-code-block");
            }
        }

        private void listItem(ListItem item, int depth, boolean inQuote) {
            int line = firstLine(item);
            if (line < 0) {
                return;
            }
            boolean ordered = item.getParent() instanceof OrderedList;
            Matcher matcher = LIST_MARKER.matcher(lineText(line));
            boolean task = item.getFirstChild() instanceof TaskListItemMarker;
            boolean checked = task && ((TaskListItemMarker) item.getFirstChild()).isChecked();
            int markerEnd = 0;
            if (matcher.find()) {
                markerEnd = matcher.end(3);
                if (task && matcher.group(4) != null) {
                    markerEnd = matcher.end(4);
                }
            }
            int ordinal = 0;
            if (ordered) {
                Integer start = ((OrderedList) item.getParent()).getMarkerStartNumber();
                int index = 0;
                for (Node before = item.getPrevious(); before != null; before = before.getPrevious()) {
                    index++;
                }
                ordinal = (start == null ? 1 : start) + index;
            }
            Kind kind = task ? Kind.TASK : ordered ? Kind.NUMBERED : Kind.BULLET;
            set(line, new BlockInfo(kind, depth, ordinal, checked, inQuote));
            if (markerEnd > 0) {
                mark(lineStarts[line], lineStarts[line] + markerEnd);
                if (checked) {
                    addStyled(lineStarts[line] + markerEnd, lineEnd(line), "md-task-done-text");
                }
            }
            for (Node child = item.getFirstChild(); child != null; child = child.getNext()) {
                if (child instanceof TaskListItemMarker) {
                    continue;
                }
                if (child instanceof org.commonmark.node.BulletList || child instanceof OrderedList) {
                    walk(child, depth, inQuote);
                } else {
                    block(child, depth, inQuote);
                }
            }
        }

        private void table(TableBlock table, boolean inQuote) {
            for (Node section = table.getFirstChild(); section != null; section = section.getNext()) {
                boolean head = section instanceof TableHead;
                if (!(head || section instanceof TableBody)) {
                    continue;
                }
                for (Node row = section.getFirstChild(); row != null; row = row.getNext()) {
                    if (!(row instanceof TableRow)) {
                        continue;
                    }
                    int line = firstLine(row);
                    if (line < 0) {
                        continue;
                    }
                    set(line, new BlockInfo(head ? Kind.TABLE_HEAD : Kind.TABLE_ROW, 0, 0, false, inQuote));
                    addStyled(lineStarts[line], lineEnd(line), head ? "md-table-head" : "md-table-text");
                    pipes(line);
                    inlines(row);
                    if (head && line + 1 < lineStarts.length) {
                        set(line + 1, new BlockInfo(Kind.TABLE_RULE, 0, 0, false, inQuote));
                        addStyled(lineStarts[line + 1], lineEnd(line + 1), "md-table-rule");
                    }
                }
            }
        }

        private void pipes(int line) {
            String content = lineText(line);
            for (int index = 0; index < content.length(); index++) {
                if (content.charAt(index) == '|' && (index == 0 || content.charAt(index - 1) != '\\')) {
                    addStyled(lineStarts[line] + index, lineStarts[line] + index + 1, "md-table-pipe");
                }
            }
        }

        private void quoteMarkers(BlockQuote quote) {
            int first = firstLine(quote);
            int last = lastLine(quote);
            for (int line = Math.max(0, first); line >= 0 && line <= last && line < lineStarts.length; line++) {
                Matcher matcher = QUOTE_MARKER.matcher(lineText(line));
                if (matcher.find() && matcher.end() > 0) {
                    mark(lineStarts[line], lineStarts[line] + matcher.end());
                }
            }
        }

        private void flagQuote(Node node, boolean inQuote) {
            if (!inQuote) {
                return;
            }
            int first = firstLine(node);
            int last = lastLine(node);
            for (int line = Math.max(0, first); line >= 0 && line <= last && line < blocks.size(); line++) {
                BlockInfo info = blocks.get(line);
                if (!info.quote()) {
                    blocks.set(line, new BlockInfo(info.kind(), info.level(), info.ordinal(), info.checked(), true));
                }
            }
        }

        // ---- inline ----

        private void inlines(Node parent) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
                switch (node) {
                    case StrongEmphasis ignored -> delimited(node, "md-strong");
                    case Emphasis ignored -> delimited(node, "md-em");
                    case Strikethrough ignored -> delimited(node, "md-strike");
                    case Ins ignored -> delimited(node, "md-underline");
                    case HighlightExtension.Highlight highlight ->
                            delimited(node, "md-highlight", "md-highlight-" + highlight.color());
                    case Code code -> code(code);
                    case Link ignored -> link(node);
                    case Image ignored -> link(node);
                    default -> inlines(node);
                }
            }
        }

        private void delimited(Node node, String... styleClasses) {
            int start = styleStart(node);
            int end = styleEnd(node);
            if (start < 0 || end <= start) {
                inlines(node);
                return;
            }
            int open = ((Delimited) node).getOpeningDelimiter().length();
            int close = ((Delimited) node).getClosingDelimiter().length();
            if (end - start < open + close) {
                inlines(node);
                return;
            }
            mark(start, start + open);
            mark(end - close, end);
            for (String styleClass : styleClasses) {
                addStyled(start + open, end - close, styleClass);
            }
            inlines(node);
        }

        private void code(Code code) {
            int start = styleStart(code);
            int end = styleEnd(code);
            if (start < 0 || end <= start) {
                return;
            }
            int ticks = 0;
            while (start + ticks < end && text.charAt(start + ticks) == '`') {
                ticks++;
            }
            if (ticks == 0 || end - start < 2 * ticks) {
                return;
            }
            mark(start, start + ticks);
            mark(end - ticks, end);
            addStyled(start + ticks, end - ticks, "md-code-inline");
        }

        private void link(Node link) {
            int start = styleStart(link);
            int end = styleEnd(link);
            Node first = link.getFirstChild();
            Node last = link.getLastChild();
            if (start < 0 || end <= start) {
                return;
            }
            if (first == null) {
                mark(start, end);
                return;
            }
            int textStart = styleStart(first);
            int textEnd = styleEnd(last);
            if (textStart < start || textEnd > end || textEnd <= textStart) {
                return;
            }
            mark(start, textStart);
            mark(textEnd, end);
            addStyled(textStart, textEnd, "md-link");
            String destination = link instanceof Link plain ? plain.getDestination() : ((Image) link).getDestination();
            if (destination != null && !destination.isBlank()) {
                links.add(new LinkRange(textStart, textEnd, destination));
            }
            inlines(link);
        }

        // ---- positions ----

        private int firstLine(Node node) {
            List<SourceSpan> spans = node.getSourceSpans();
            return spans.isEmpty() ? -1 : Math.min(spans.getFirst().getLineIndex(), lineStarts.length - 1);
        }

        private int lastLine(Node node) {
            List<SourceSpan> spans = node.getSourceSpans();
            return spans.isEmpty() ? -1 : Math.min(spans.getLast().getLineIndex(), lineStarts.length - 1);
        }

        private int styleStart(Node node) {
            List<SourceSpan> spans = node.getSourceSpans();
            return spans.isEmpty() ? -1 : clamp(spans.getFirst().getInputIndex());
        }

        private int styleEnd(Node node) {
            List<SourceSpan> spans = node.getSourceSpans();
            if (spans.isEmpty()) {
                return -1;
            }
            SourceSpan last = spans.getLast();
            return clamp(last.getInputIndex() + last.getLength());
        }

        private int clamp(int offset) {
            return Math.max(0, Math.min(text.length(), offset));
        }

        private String lineText(int line) {
            return text.substring(lineStarts[line], lineEnd(line));
        }

        private int lineEnd(int line) {
            return line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
        }

        private void set(int line, BlockInfo info) {
            if (line >= 0 && line < blocks.size()) {
                blocks.set(line, info);
            }
        }

        private void mark(int start, int end) {
            if (start >= 0 && end > start) {
                marks.add(new Mark(clamp(start), clamp(end)));
            }
        }

        private void addStyled(int start, int end, String styleClass) {
            if (start >= 0 && end > start) {
                styled.add(new Styled(clamp(start), clamp(end), styleClass));
            }
        }
    }
}
