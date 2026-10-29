package com.edutrack.desktop.ui.markdown;

import org.commonmark.Extension;
import org.commonmark.node.CustomNode;
import org.commonmark.node.Delimited;
import org.commonmark.node.Node;
import org.commonmark.node.Nodes;
import org.commonmark.node.SourceSpans;
import org.commonmark.node.Text;
import org.commonmark.parser.Parser;
import org.commonmark.parser.delimiter.DelimiterProcessor;
import org.commonmark.parser.delimiter.DelimiterRun;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Highlighted text, written {@code ==like this==}. CommonMark has no highlight, so this small extension
 * adds one with the same rules as emphasis: the markers must hug the text, so {@code a == b} stays as it is.
 * A colour is written right after the opening markers, {@code =={green}like this==}; without one the
 * highlight is yellow. Readers that do not know colours still show the text highlighted (with the
 * {@code {green}} in front of it), and a name that is not one of {@link #COLORS} is plain text.
 * Both the note renderer and the note editor parse with it.
 */
public final class HighlightExtension implements Parser.ParserExtension {

    /** The highlight colours; the first is the default, written without a marker. */
    public static final List<String> COLORS = List.of("yellow", "green", "blue", "pink", "orange", "purple");

    public static final String DEFAULT_COLOR = COLORS.getFirst();

    private static final Pattern COLOR_MARKER = Pattern.compile("^\\{(" + String.join("|", COLORS) + ")}");

    /** What goes between the opening {@code ==} and the text for {@code color}: nothing for yellow. */
    public static String marker(String color) {
        return color == null || DEFAULT_COLOR.equals(color) || !COLORS.contains(color) ? "" : "{" + color + "}";
    }

    private HighlightExtension() {
    }

    public static Extension create() {
        return new HighlightExtension();
    }

    @Override
    public void extend(Parser.Builder builder) {
        builder.customDelimiterProcessor(new HighlightDelimiterProcessor());
    }

    /** The node for {@code ==text==} and {@code =={color}text==}. */
    public static final class Highlight extends CustomNode implements Delimited {

        static final String DELIMITER = "==";

        private String color = DEFAULT_COLOR;
        private boolean colorWritten;

        /** One of {@link #COLORS}. */
        public String color() {
            return color;
        }

        /** The opening markers as written, so {@code =={green}} counts as nine characters of syntax. */
        @Override
        public String getOpeningDelimiter() {
            return colorWritten ? DELIMITER + "{" + color + "}" : DELIMITER;
        }

        @Override
        public String getClosingDelimiter() {
            return DELIMITER;
        }
    }

    private static final class HighlightDelimiterProcessor implements DelimiterProcessor {

        @Override
        public char getOpeningCharacter() {
            return '=';
        }

        @Override
        public char getClosingCharacter() {
            return '=';
        }

        @Override
        public int getMinLength() {
            return 2;
        }

        @Override
        public int process(DelimiterRun openingRun, DelimiterRun closingRun) {
            if (openingRun.length() < 2 || closingRun.length() < 2) {
                return 0;
            }
            Text opener = openingRun.getOpener();
            Node highlight = new Highlight();
            SourceSpans sourceSpans = new SourceSpans();
            sourceSpans.addAllFrom(openingRun.getOpeners(2));
            for (Node node : Nodes.between(opener, closingRun.getCloser())) {
                highlight.appendChild(node);
                sourceSpans.addAll(node.getSourceSpans());
            }
            sourceSpans.addAllFrom(closingRun.getClosers(2));
            highlight.setSourceSpans(sourceSpans.getSourceSpans());
            readColor((Highlight) highlight);
            opener.insertAfter(highlight);
            return 2;
        }

        /** Moves a leading {@code {color}} from the text into the node. */
        private static void readColor(Highlight highlight) {
            if (!(highlight.getFirstChild() instanceof Text first)) {
                return;
            }
            Matcher marker = COLOR_MARKER.matcher(first.getLiteral());
            if (!marker.find()) {
                return;
            }
            highlight.color = marker.group(1);
            highlight.colorWritten = true;
            String rest = first.getLiteral().substring(marker.end());
            if (rest.isEmpty()) {
                first.unlink();
            } else {
                first.setLiteral(rest);
            }
        }
    }
}
