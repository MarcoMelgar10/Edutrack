package com.edutrack.desktop.ui.markdown;

import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.ins.Ins;
import org.commonmark.ext.ins.InsExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Turns Markdown (CommonMark plus the GFM tables, strikethrough and task-list extensions, {@code ++underline++}
 * and {@code ==highlight==}) into
 * plain JavaFX nodes — no WebView. Styling lives in CSS ({@code .md-*} classes in {@code base.css});
 * nested inline styles add one class each.
 */
final class MarkdownRenderer {

    private final Parser parser = Parser.builder()
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(),
                    TaskListItemsExtension.create(), InsExtension.create(), HighlightExtension.create()))
            .build();
    private final Consumer<String> onLink;

    MarkdownRenderer(Consumer<String> onLink) {
        this.onLink = onLink;
    }

    List<Node> render(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return List.of();
        }
        return blocks(parser.parse(markdown));
    }

    private List<Node> blocks(org.commonmark.node.Node parent) {
        List<Node> nodes = new ArrayList<>();
        for (org.commonmark.node.Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            Node rendered = block(child);
            if (rendered != null) {
                nodes.add(rendered);
            }
        }
        return nodes;
    }

    private Node block(org.commonmark.node.Node node) {
        return switch (node) {
            case Heading heading -> {
                TextFlow flow = flow("md-h" + Math.min(heading.getLevel(), 3));
                inlines(heading, flow, List.of());
                yield flow;
            }
            case Paragraph paragraph -> {
                TextFlow flow = flow("md-paragraph");
                inlines(paragraph, flow, List.of());
                yield flow;
            }
            case FencedCodeBlock code -> codeBlock(code.getLiteral());
            case IndentedCodeBlock code -> codeBlock(code.getLiteral());
            case BulletList list -> list(list, false, 1);
            case OrderedList list -> list(list, true,
                    list.getMarkerStartNumber() == null ? 1 : list.getMarkerStartNumber());
            case BlockQuote quote -> box("md-quote", blocks(quote));
            case TableBlock table -> table(table);
            // Rendered by list(); kept out of the item's own blocks.
            case TaskListItemMarker marker -> null;
            case ThematicBreak rule -> {
                Separator separator = new Separator();
                separator.getStyleClass().add("md-rule");
                yield separator;
            }
            case HtmlBlock html -> {
                TextFlow flow = flow("md-paragraph");
                flow.getChildren().add(text(html.getLiteral().strip(), List.of()));
                yield flow;
            }
            default -> {
                TextFlow flow = flow("md-paragraph");
                inlines(node, flow, List.of());
                yield flow.getChildren().isEmpty() ? null : flow;
            }
        };
    }

    private Node list(org.commonmark.node.Node list, boolean ordered, int first) {
        VBox box = new VBox();
        box.getStyleClass().add("md-list");
        int number = first;
        for (org.commonmark.node.Node item = list.getFirstChild(); item != null; item = item.getNext()) {
            if (!(item instanceof ListItem)) {
                continue;
            }
            Label marker = new Label(ordered ? (number++) + "." : "•");
            if (item.getFirstChild() instanceof TaskListItemMarker task) {
                // Same column as a bullet: the checkbox is the marker's graphic.
                marker.setText(null);
                marker.setGraphic(taskBox(task.isChecked()));
            }
            marker.getStyleClass().add("md-list-marker");
            marker.setMinWidth(Region.USE_PREF_SIZE);
            VBox content = box("md-list-item", blocks(item));
            HBox.setHgrow(content, Priority.ALWAYS);
            HBox row = new HBox(marker, content);
            row.getStyleClass().add("md-list-row");
            box.getChildren().add(row);
        }
        return box;
    }

    /** A read-only checkbox: an outlined square, filled with a check when the task is done. */
    private static Region taskBox(boolean checked) {
        StackPane box = new StackPane();
        box.getStyleClass().add("md-task");
        if (checked) {
            box.getStyleClass().add("md-task-done");
            FontIcon check = new FontIcon(Feather.CHECK);
            check.getStyleClass().add("md-task-check");
            box.getChildren().add(check);
        }
        box.setAccessibleText(checked ? "Done" : "To do");
        return box;
    }

    /** Header row first, then the body rows; each cell keeps its inline styles and alignment. */
    private Node table(TableBlock table) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("md-table");
        int row = 0;
        int columns = 0;
        for (org.commonmark.node.Node section = table.getFirstChild(); section != null; section = section.getNext()) {
            for (org.commonmark.node.Node line = section.getFirstChild(); line != null; line = line.getNext()) {
                if (!(line instanceof TableRow)) {
                    continue;
                }
                int column = 0;
                for (org.commonmark.node.Node cell = line.getFirstChild(); cell != null; cell = cell.getNext()) {
                    if (cell instanceof TableCell tableCell) {
                        grid.add(tableCell(tableCell), column++, row);
                    }
                }
                columns = Math.max(columns, column);
                row++;
            }
        }
        for (int i = 0; i < columns; i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setHgrow(Priority.SOMETIMES);
            grid.getColumnConstraints().add(constraints);
        }
        return box("md-table-box", List.of(grid));
    }

    private Node tableCell(TableCell cell) {
        TextFlow flow = flow("md-table-cell");
        if (cell.isHeader()) {
            flow.getStyleClass().add("md-table-head");
        }
        flow.setTextAlignment(switch (cell.getAlignment() == null ? TableCell.Alignment.LEFT : cell.getAlignment()) {
            case CENTER -> TextAlignment.CENTER;
            case RIGHT -> TextAlignment.RIGHT;
            default -> TextAlignment.LEFT;
        });
        flow.setMaxWidth(Double.MAX_VALUE);
        inlines(cell, flow, cell.isHeader() ? List.of("md-strong") : List.of());
        return flow;
    }

    private static Node codeBlock(String literal) {
        String code = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
        Label label = new Label(code);
        label.getStyleClass().add("md-code-block");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return box("md-code", List.of(label));
    }

    private static VBox box(String styleClass, List<Node> children) {
        VBox box = new VBox();
        box.getStyleClass().add(styleClass);
        box.getChildren().addAll(children);
        return box;
    }

    private static TextFlow flow(String styleClass) {
        TextFlow flow = new TextFlow();
        flow.getStyleClass().add(styleClass);
        return flow;
    }

    /** Appends the inline children of {@code parent}; {@code styles} are the emphasis classes in effect. */
    private void inlines(org.commonmark.node.Node parent, TextFlow flow, List<String> styles) {
        for (org.commonmark.node.Node child = parent.getFirstChild(); child != null; child = child.getNext()) {
            switch (child) {
                case org.commonmark.node.Text literal -> flow.getChildren().add(text(literal.getLiteral(), styles));
                case Emphasis emphasis -> inlines(emphasis, flow, with(styles, "md-em"));
                case StrongEmphasis strong -> inlines(strong, flow, with(styles, "md-strong"));
                case Strikethrough strike -> inlines(strike, flow, with(styles, "md-strike"));
                case Ins underline -> inlines(underline, flow, with(styles, "md-underline"));
                case HighlightExtension.Highlight highlight -> inlines(highlight, flow,
                        with(with(styles, "md-highlight"), "md-highlight-" + highlight.color()));
                case Code code -> {
                    Label label = new Label(code.getLiteral());
                    label.getStyleClass().add("md-code-inline");
                    flow.getChildren().add(label);
                }
                case Link link -> {
                    Hyperlink hyperlink = new Hyperlink(plainText(link));
                    hyperlink.getStyleClass().add("md-link");
                    String destination = link.getDestination();
                    hyperlink.setOnAction(click -> onLink.accept(destination));
                    flow.getChildren().add(hyperlink);
                }
                case SoftLineBreak softBreak -> flow.getChildren().add(text(" ", styles));
                case HardLineBreak hardBreak -> flow.getChildren().add(text("\n", styles));
                case Image image -> flow.getChildren().add(text(plainText(image), styles));
                case HtmlInline html -> flow.getChildren().add(text(html.getLiteral(), styles));
                default -> inlines(child, flow, styles);
            }
        }
    }

    private static Text text(String content, List<String> styles) {
        Text text = new Text(content);
        text.getStyleClass().add("md-text");
        text.getStyleClass().addAll(styles);
        return text;
    }

    private static List<String> with(List<String> styles, String added) {
        if (styles.contains(added)) {
            return styles;
        }
        List<String> combined = new ArrayList<>(styles);
        combined.add(added);
        return combined;
    }

    /** The text of an inline node and its children, without styling (link labels, image alt text). */
    private static String plainText(org.commonmark.node.Node node) {
        StringBuilder out = new StringBuilder();
        for (org.commonmark.node.Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            switch (child) {
                case org.commonmark.node.Text literal -> out.append(literal.getLiteral());
                case Code code -> out.append(code.getLiteral());
                case SoftLineBreak softBreak -> out.append(' ');
                case HardLineBreak hardBreak -> out.append(' ');
                default -> out.append(plainText(child));
            }
        }
        return out.toString();
    }
}
