package com.edutrack.desktop.ui.notes;

import com.edutrack.desktop.ui.markdown.HighlightExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The edits the note editor's tools make to the Markdown text: wrap a selection in bold, turn lines into a
 * heading or a list, continue a list on Enter, indent with Tab, and so on. Every method is a pure function of
 * the text and the selection that returns an {@link Edit}: the smallest replacement that makes the change and
 * where the selection ends up. The editor applies the edit as one replacement, so undo works step by step, and
 * text the tool did not touch is never rewritten.
 */
public final class MarkdownEdits {

    /** The blocks a line can be turned into. */
    public enum Block { TEXT, H1, H2, H3, BULLET, NUMBERED, TASK, QUOTE, CODE }

    /**
     * Replace {@code [from, to)} of the old text with {@code replacement}; afterwards select
     * {@code [selStart, selEnd]} (positions in the new text).
     */
    public record Edit(int from, int to, String replacement, int selStart, int selEnd) {

        public String applyTo(String text) {
            return text.substring(0, from) + replacement + text.substring(to);
        }
    }

    /** One level of nesting: four spaces keep a child item inside its parent whatever the parent's marker is. */
    static final String INDENT = "    ";

    private static final Pattern LIST = Pattern.compile("^(\\s*)([-*+]|(\\d{1,9})([.)]))([ \\t]+)(\\[[ xX]\\][ \\t]+)?");
    private static final Pattern QUOTE = Pattern.compile("^(\\s{0,3}(?:>[ \\t]?)+)");
    private static final Pattern HEADING = Pattern.compile("^(\\s{0,3})(#{1,6})[ \\t]+");
    private static final Pattern FENCE = Pattern.compile("^\\s{0,3}(`{3,}|~{3,})");
    private static final Pattern TASK_BOX = Pattern.compile("\\[([ xX])\\]");

    private MarkdownEdits() {
    }

    // ---------- inline formatting ----------

    /**
     * Wraps the selection (or, with no selection, the word under the caret) in {@code marker}, or removes the
     * marker when it is already there. Spaces at the ends of a selection stay outside the markers.
     */
    public static Edit toggleInline(String text, int selStart, int selEnd, String marker) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        if (start == end) {
            int[] word = wordAround(text, start);
            if (word == null) {
                return new Edit(start, start, marker + marker, start + marker.length(), start + marker.length());
            }
            start = word[0];
            end = word[1];
        } else {
            while (start < end && Character.isWhitespace(text.charAt(start))) {
                start++;
            }
            while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
                end--;
            }
            if (start == end) {
                return new Edit(selStart, selStart, marker + marker, selStart + marker.length(), selStart + marker.length());
            }
        }
        int length = marker.length();
        char symbol = marker.charAt(0);
        int run = Math.min(runBefore(text, start, symbol), runAfter(text, end, symbol));
        boolean wrapped = switch (marker) {
            case "*" -> run % 2 == 1;
            case "**", "~~", "++", "==" -> run >= 2;
            default -> run >= length;
        };
        if (wrapped) {
            return new Edit(start - length, end + length, text.substring(start, end), start - length, end - length);
        }
        return new Edit(start, end, marker + text.substring(start, end) + marker, start + length, end + length);
    }

    /** Which inline markers surround the caret or selection: for the toolbar to show what is on. */
    public static boolean isWrapped(String text, int selStart, int selEnd, String marker) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        if (start == end) {
            int[] word = wordAround(text, start);
            if (word == null) {
                return false;
            }
            start = word[0];
            end = word[1];
        }
        char symbol = marker.charAt(0);
        int run = Math.min(runBefore(text, start, symbol), runAfter(text, end, symbol));
        return switch (marker) {
            case "*" -> run % 2 == 1;
            case "**", "~~", "++", "==" -> run >= 2;
            default -> run >= marker.length();
        };
    }

    // ---------- highlight ----------

    private static final Pattern HIGHLIGHT = Pattern.compile("==(\\{(?:" + String.join("|", HighlightExtension.COLORS)
            + ")})?(?![=\\s])(.+?)(?<![=\\s])==");

    /** An existing highlight: where it starts and ends, its colour and the span of its text. */
    private record Span(int start, int end, String color, int textStart, int textEnd) {
    }

    /**
     * The colour of the highlight that holds the caret or selection (one of
     * {@link HighlightExtension#COLORS}), or {@code null} when the text there is not highlighted.
     */
    public static String highlightColor(String text, int selStart, int selEnd) {
        Span span = highlightAround(text, Math.min(selStart, selEnd), Math.max(selStart, selEnd));
        return span == null ? null : span.color();
    }

    /**
     * Highlights the selection (or the word under the caret) in {@code color}. Text that is already
     * highlighted changes colour in place instead of being wrapped again; {@code null} takes the
     * highlight off. With nothing to wrap, an empty pair of markers is inserted for typing into.
     */
    public static Edit applyHighlight(String text, int selStart, int selEnd, String color) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        Span span = highlightAround(text, start, end);
        if (span != null) {
            if (color == null) {
                int shift = span.textStart() - span.start();
                return new Edit(span.start(), span.end(), text.substring(span.textStart(), span.textEnd()),
                        Math.max(start, span.textStart()) - shift, Math.min(end, span.textEnd()) - shift);
            }
            String opening = "==" + HighlightExtension.marker(color);
            int shift = opening.length() - (span.textStart() - span.start());
            return new Edit(span.start(), span.textStart(), opening,
                    Math.max(start, span.textStart()) + shift, Math.min(end, span.textEnd()) + shift);
        }
        if (color == null) {
            return null;
        }
        String opening = "==" + HighlightExtension.marker(color);
        int[] range = wrapRange(text, start, end);
        if (range == null) {
            return new Edit(start, end, opening + "==", start + opening.length(), start + opening.length());
        }
        return new Edit(range[0], range[1], opening + text.substring(range[0], range[1]) + "==",
                range[0] + opening.length(), range[1] + opening.length());
    }

    /** Takes the highlight off when the selection has one, whatever its colour, and otherwise applies {@code color}. */
    public static Edit toggleHighlight(String text, int selStart, int selEnd, String color) {
        return applyHighlight(text, selStart, selEnd, highlightColor(text, selStart, selEnd) == null ? color : null);
    }

    private static Span highlightAround(String text, int start, int end) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, start) - 1) + 1;
        int lineEnd = text.indexOf('\n', end);
        lineEnd = lineEnd < 0 ? text.length() : lineEnd;
        if (lineStart > start || end > lineEnd) {
            return null;
        }
        Matcher matcher = HIGHLIGHT.matcher(text.substring(lineStart, lineEnd));
        while (matcher.find()) {
            int from = lineStart + matcher.start();
            int to = lineStart + matcher.end();
            if (from <= start && end <= to) {
                String marker = matcher.group(1);
                String color = marker == null ? HighlightExtension.DEFAULT_COLOR : marker.substring(1, marker.length() - 1);
                return new Span(from, to, color, lineStart + matcher.start(2), lineStart + matcher.end(2));
            }
        }
        return null;
    }

    /** What to wrap: the selection without its edge spaces, or the word at the caret; {@code null} when there is none. */
    private static int[] wrapRange(String text, int start, int end) {
        if (start == end) {
            return wordAround(text, start);
        }
        while (start < end && Character.isWhitespace(text.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return start == end ? null : new int[]{start, end};
    }

    /** {@code [selected](url)}; with nothing selected the link text is "link" and gets selected. */
    public static Edit makeLink(String text, int selStart, int selEnd, String url) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        String label = start == end ? "link" : text.substring(start, end);
        String replacement = "[" + label + "](" + url.strip() + ")";
        return new Edit(start, end, replacement, start + 1, start + 1 + label.length());
    }

    /** Puts {@code insert} where the selection is, caret after it. */
    public static Edit insertText(String text, int selStart, int selEnd, String insert) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        return new Edit(start, end, insert, start + insert.length(), start + insert.length());
    }

    private static int[] wordAround(String text, int caret) {
        int start = caret;
        int end = caret;
        while (start > 0 && isWordChar(text.charAt(start - 1))) {
            start--;
        }
        while (end < text.length() && isWordChar(text.charAt(end))) {
            end++;
        }
        return start == end ? null : new int[]{start, end};
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int runBefore(String text, int offset, char symbol) {
        int count = 0;
        while (offset - count - 1 >= 0 && text.charAt(offset - count - 1) == symbol) {
            count++;
        }
        return count;
    }

    private static int runAfter(String text, int offset, char symbol) {
        int count = 0;
        while (offset + count < text.length() && text.charAt(offset + count) == symbol) {
            count++;
        }
        return count;
    }

    // ---------- blocks ----------

    /**
     * Turns the lines of the selection into {@code block}, replacing the marker a line already has instead of
     * stacking another. Applying the block a line already is turns it back into plain text. Numbered lists are
     * numbered from 1.
     */
    public static Edit setBlock(String text, int selStart, int selEnd, Block block) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        int[] lineStarts = starts(text);
        int first = lineOf(lineStarts, start);
        int last = lineOf(lineStarts, end);
        if (end > start && end == lineStarts[last] && last > first) {
            last--; // a selection that ends at the start of a line does not include that line
        }
        if (block == Block.CODE) {
            return fenceLines(text, lineStarts, first, last);
        }
        List<String> lines = new ArrayList<>();
        for (int line = first; line <= last; line++) {
            lines.add(lineText(text, lineStarts, line));
        }
        boolean single = first == last;
        boolean already = lines.stream().filter(line -> !line.isBlank() || single)
                .allMatch(line -> kindOf(line) == block);
        Block target = already ? Block.TEXT : block;

        List<String> result = new ArrayList<>();
        int number = 1;
        int caretLineDelta = 0;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            Stripped stripped = strip(line);
            if (line.isBlank() && !single) {
                result.add(line);
                continue;
            }
            String indent = target == Block.BULLET || target == Block.NUMBERED || target == Block.TASK
                    ? stripped.indent() : "";
            String prefix = switch (target) {
                case TEXT, CODE -> "";
                case H1 -> "# ";
                case H2 -> "## ";
                case H3 -> "### ";
                case BULLET -> "- ";
                case NUMBERED -> (number++) + ". ";
                case TASK -> "- [ ] ";
                case QUOTE -> "> ";
            };
            String rewritten = indent + prefix + stripped.body();
            result.add(rewritten);
            if (index == 0) {
                caretLineDelta = rewritten.length() - line.length();
            }
        }
        int from = lineStarts[first];
        int to = lineEnd(text, lineStarts, last);
        String replacement = String.join("\n", result);
        int selectStart;
        int selectEnd;
        if (start == end) {
            int column = start - lineStarts[first];
            Stripped stripped = strip(lines.getFirst());
            int bodyColumn = lines.getFirst().length() - stripped.body().length();
            int newBodyColumn = result.getFirst().length() - stripped.body().length();
            int caret = from + Math.max(newBodyColumn, column - bodyColumn + newBodyColumn);
            selectStart = Math.min(caret, from + result.getFirst().length());
            selectEnd = selectStart;
        } else {
            selectStart = from;
            selectEnd = from + replacement.length();
        }
        return new Edit(from, to, replacement, selectStart, selectEnd);
    }

    /** What block a line currently is, from its marker. */
    public static Block kindOf(String line) {
        Matcher heading = HEADING.matcher(line);
        if (heading.find()) {
            return switch (heading.group(2).length()) {
                case 1 -> Block.H1;
                case 2 -> Block.H2;
                default -> Block.H3;
            };
        }
        Matcher list = LIST.matcher(line);
        if (list.find()) {
            if (list.group(6) != null) {
                return Block.TASK;
            }
            return list.group(3) != null ? Block.NUMBERED : Block.BULLET;
        }
        return QUOTE.matcher(line).find() ? Block.QUOTE : Block.TEXT;
    }

    private record Stripped(String indent, String body) {
    }

    /** The line without its heading, quote or list marker. */
    private static Stripped strip(String line) {
        Matcher heading = HEADING.matcher(line);
        if (heading.find()) {
            return new Stripped(heading.group(1), line.substring(heading.end()));
        }
        Matcher list = LIST.matcher(line);
        if (list.find()) {
            return new Stripped(list.group(1), line.substring(list.end()));
        }
        Matcher quote = QUOTE.matcher(line);
        if (quote.find()) {
            return new Stripped("", line.substring(quote.end()));
        }
        int indentLength = 0;
        while (indentLength < line.length() && (line.charAt(indentLength) == ' ' || line.charAt(indentLength) == '\t')) {
            indentLength++;
        }
        return new Stripped(line.substring(0, indentLength), line.substring(indentLength));
    }

    private static Edit fenceLines(String text, int[] lineStarts, int first, int last) {
        int from = lineStarts[first];
        int to = lineEnd(text, lineStarts, last);
        String body = text.substring(from, to);
        String replacement = "```\n" + body + "\n```";
        // Caret on the first line of code, ready to type when the lines were empty.
        int caret = from + 4;
        return new Edit(from, to, replacement, caret, caret + body.length());
    }

    // ---------- blocks that are inserted ----------

    public static Edit insertRule(String text, int caret) {
        return insertBlock(text, caret, "---", 3);
    }

    /** A table with a header row, the rule row and {@code rows} empty rows; the caret goes in the first header cell. */
    public static Edit insertTable(String text, int caret, int rows, int columns) {
        StringBuilder table = new StringBuilder();
        table.append("|").append(" Column |".repeat(columns)).append("\n");
        table.append("|").append(" ------ |".repeat(columns)).append("\n");
        for (int row = 0; row < rows; row++) {
            table.append("|").append("        |".repeat(columns));
            if (row < rows - 1) {
                table.append("\n");
            }
        }
        return insertBlock(text, caret, table.toString(), 2);
    }

    public static Edit insertCodeBlock(String text, int caret) {
        return insertBlock(text, caret, "```\n\n```", 4);
    }

    /**
     * Puts a block on its own lines at the caret: replacing an empty line, or after the current one with a blank
     * line between (a rule right under a paragraph line would turn that line into a heading).
     */
    private static Edit insertBlock(String text, int caret, String block, int caretInBlock) {
        int[] lineStarts = starts(text);
        int line = lineOf(lineStarts, caret);
        boolean blank = lineText(text, lineStarts, line).isBlank();
        boolean hasNext = line + 1 < lineStarts.length;
        boolean nextBlank = hasNext && lineText(text, lineStarts, line + 1).isBlank();
        int from;
        int to;
        String before;
        if (blank) {
            // The empty line is replaced; its own newline stays and ends the block.
            from = lineStarts[line];
            to = lineEnd(text, lineStarts, line);
            before = line > 0 && !lineText(text, lineStarts, line - 1).isBlank() ? "\n" : "";
        } else {
            from = lineEnd(text, lineStarts, line);
            to = from;
            before = "\n\n";
        }
        // Text right under the block needs a blank line (a table would swallow it as a row); at the end of the
        // note a newline leaves room to keep typing.
        String after = !hasNext || !nextBlank && !(blank && !hasNext) ? "\n" : "";
        if (blank && hasNext && nextBlank) {
            after = "";
        }
        String replacement = before + block + after;
        int caretAt = from + before.length() + caretInBlock;
        return new Edit(from, to, replacement, caretAt, caretAt);
    }

    // ---------- keys ----------

    /**
     * Enter inside a list item or a quote: start the next item (numbers go up, a task starts unchecked); on an
     * empty item, take the item back one level or end the list. {@code null} means "do what Enter normally does".
     */
    public static Edit onEnter(String text, int caret) {
        int[] lineStarts = starts(text);
        int line = lineOf(lineStarts, caret);
        if (insideFence(text, lineStarts, line)) {
            return null;
        }
        String content = lineText(text, lineStarts, line);
        int lineStart = lineStarts[line];
        int column = caret - lineStart;

        Matcher list = LIST.matcher(content);
        if (list.find() && column >= list.end()) {
            String body = content.substring(list.end());
            if (body.isBlank()) {
                String indent = list.group(1);
                if (indent.length() >= INDENT.length()) {
                    String outdented = content.substring(INDENT.length());
                    return new Edit(lineStart, lineStart + content.length(), outdented, lineStart + outdented.length(),
                            lineStart + outdented.length());
                }
                return new Edit(lineStart, lineStart + content.length(), "", lineStart, lineStart);
            }
            String marker = list.group(3) != null
                    ? (Integer.parseInt(list.group(3)) + 1) + list.group(4)
                    : list.group(2);
            String next = list.group(1) + marker + list.group(5) + (list.group(6) != null ? "[ ] " : "");
            return new Edit(caret, caret, "\n" + next, caret + 1 + next.length(), caret + 1 + next.length());
        }
        Matcher quote = QUOTE.matcher(content);
        if (quote.find() && column >= quote.end()) {
            String body = content.substring(quote.end());
            if (body.isBlank()) {
                return new Edit(lineStart, lineStart + content.length(), "", lineStart, lineStart);
            }
            String next = quote.group(1).endsWith(" ") ? quote.group(1) : quote.group(1) + " ";
            return new Edit(caret, caret, "\n" + next, caret + 1 + next.length(), caret + 1 + next.length());
        }
        return null;
    }

    /**
     * Tab (or Shift+Tab) on list items: nest the item one level under the one above, or take it back out.
     * {@code null} when no selected line is a list item that can move.
     */
    public static Edit onTab(String text, int selStart, int selEnd, boolean shift) {
        int start = Math.min(selStart, selEnd);
        int end = Math.max(selStart, selEnd);
        int[] lineStarts = starts(text);
        int first = lineOf(lineStarts, start);
        int last = lineOf(lineStarts, end);
        List<String> lines = new ArrayList<>();
        int firstDelta = 0;
        int totalDelta = 0;
        boolean changed = false;
        for (int line = first; line <= last; line++) {
            String content = lineText(text, lineStarts, line);
            String moved = content;
            Matcher list = LIST.matcher(content);
            if (list.find()) {
                if (shift) {
                    int remove = content.startsWith("\t") ? 1 : Math.min(INDENT.length(), list.group(1).length());
                    moved = content.substring(remove);
                } else if (canNest(text, lineStarts, line, list.group(1).length())) {
                    moved = INDENT + content;
                }
            }
            if (!moved.equals(content)) {
                changed = true;
                if (line == first) {
                    firstDelta = moved.length() - content.length();
                }
                totalDelta += moved.length() - content.length();
            }
            lines.add(moved);
        }
        if (!changed) {
            return null;
        }
        int from = lineStarts[first];
        int to = lineEnd(text, lineStarts, last);
        return new Edit(from, to, String.join("\n", lines), Math.max(from, start + firstDelta),
                Math.max(from, end + totalDelta));
    }

    private static boolean canNest(String text, int[] lineStarts, int line, int indentLength) {
        if (line == 0) {
            return false;
        }
        Matcher above = LIST.matcher(lineText(text, lineStarts, line - 1));
        return above.find() && indentLength <= above.group(1).length();
    }

    /** Backspace right after a block's marker removes the marker (the line becomes plain text). */
    public static Edit onBackspace(String text, int caret) {
        int[] lineStarts = starts(text);
        int line = lineOf(lineStarts, caret);
        String content = lineText(text, lineStarts, line);
        int column = caret - lineStarts[line];
        int markerEnd = markerEnd(content);
        if (markerEnd > 0 && column == markerEnd && markerEnd <= content.length()) {
            int start = lineStarts[line];
            return new Edit(start, start + markerEnd, "", start, start);
        }
        return null;
    }

    private static int markerEnd(String content) {
        Matcher heading = HEADING.matcher(content);
        if (heading.find()) {
            return heading.end();
        }
        Matcher list = LIST.matcher(content);
        if (list.find()) {
            return list.end();
        }
        Matcher quote = QUOTE.matcher(content);
        return quote.find() ? quote.end() : 0;
    }

    /** Checks or unchecks the task on a line; {@code null} when the line is not a task. */
    public static Edit toggleTask(String text, int line) {
        int[] lineStarts = starts(text);
        if (line < 0 || line >= lineStarts.length) {
            return null;
        }
        String content = lineText(text, lineStarts, line);
        Matcher list = LIST.matcher(content);
        if (!list.find() || list.group(6) == null) {
            return null;
        }
        Matcher box = TASK_BOX.matcher(content);
        if (!box.find(list.start(6))) {
            return null;
        }
        boolean done = !box.group(1).equals(" ");
        int at = lineStarts[line] + box.start(1);
        return new Edit(at, at + 1, done ? " " : "x", at + 1, at + 1);
    }

    // ---------- helpers ----------

    private static boolean insideFence(String text, int[] lineStarts, int line) {
        int fences = 0;
        for (int before = 0; before < line; before++) {
            if (FENCE.matcher(lineText(text, lineStarts, before)).find()) {
                fences++;
            }
        }
        return fences % 2 == 1;
    }

    private static int[] starts(String text) {
        return MarkdownStyler.lineStarts(text);
    }

    private static int lineOf(int[] lineStarts, int offset) {
        int found = java.util.Arrays.binarySearch(lineStarts, offset);
        return found >= 0 ? found : -found - 2;
    }

    private static int lineEnd(String text, int[] lineStarts, int line) {
        return line + 1 < lineStarts.length ? lineStarts[line + 1] - 1 : text.length();
    }

    private static String lineText(String text, int[] lineStarts, int line) {
        return text.substring(lineStarts[line], lineEnd(text, lineStarts, line));
    }
}
