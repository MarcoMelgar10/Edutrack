package com.edutrack.desktop.ui.markdown;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.assertj.core.api.Assertions.assertThat;

/** Needs the FX toolkit (Text and controls), started once for the whole class. */
class MarkdownRendererTest {

    private final List<String> clicked = new ArrayList<>();
    private final MarkdownRenderer renderer = new MarkdownRenderer(clicked::add);

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

    private List<Node> render(String markdown) {
        return renderer.render(markdown);
    }

    private static List<Node> all(Node root) {
        List<Node> nodes = new ArrayList<>();
        nodes.add(root);
        // A label's graphic only becomes a child once the label is skinned; walk it directly.
        if (root instanceof Labeled labeled && labeled.getGraphic() != null) {
            nodes.addAll(all(labeled.getGraphic()));
        } else if (root instanceof Parent parent) {
            parent.getChildrenUnmodifiable().forEach(child -> nodes.addAll(all(child)));
        }
        return nodes;
    }

    private static List<Node> withClass(List<Node> roots, String styleClass) {
        return roots.stream().flatMap(root -> all(root).stream())
                .filter(node -> node.getStyleClass().contains(styleClass)).toList();
    }

    private static String textOf(Node node) {
        StringBuilder out = new StringBuilder();
        for (Node n : all(node)) {
            if (n instanceof Text text && !(n instanceof FontIcon)) {
                out.append(text.getText());
            }
        }
        return out.toString();
    }

    @Test
    void blankInputRendersNothing() {
        assertThat(render("")).isEmpty();
        assertThat(render("  \n ")).isEmpty();
        assertThat(render(null)).isEmpty();
    }

    @Test
    void theViewIsEmptyForBlankInput() {
        MarkdownView view = new MarkdownView();
        view.setMarkdown("# Title");
        assertThat(view.getChildren()).hasSize(1);

        view.setMarkdown("");

        assertThat(view.getChildren()).isEmpty();
    }

    @Test
    void headingsUseTheirLevelAndDeeperOnesRenderAsLevelThree() {
        List<Node> nodes = render("# One\n\n## Two\n\n### Three\n\n###### Six");

        assertThat(nodes).hasSize(4).allMatch(node -> node instanceof TextFlow);
        assertThat(nodes.get(0).getStyleClass()).contains("md-h1");
        assertThat(nodes.get(1).getStyleClass()).contains("md-h2");
        assertThat(nodes.get(2).getStyleClass()).contains("md-h3");
        assertThat(nodes.get(3).getStyleClass()).contains("md-h3");
        assertThat(textOf(nodes.get(0))).isEqualTo("One");
    }

    @Test
    void paragraphsKeepSoftBreaksAsSpacesAndHardBreaksAsNewlines() {
        List<Node> nodes = render("first\nsecond  \nthird");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().getStyleClass()).contains("md-paragraph");
        assertThat(textOf(nodes.getFirst())).isEqualTo("first second\nthird");
    }

    @Test
    void boldAndItalicGetTheirStyleClasses() {
        List<Node> nodes = render("a **bold** and *italic* word");

        List<Node> strong = withClass(nodes, "md-strong");
        List<Node> em = withClass(nodes, "md-em");
        assertThat(strong).hasSize(1);
        assertThat(((Text) strong.getFirst()).getText()).isEqualTo("bold");
        assertThat(em).hasSize(1);
        assertThat(((Text) em.getFirst()).getText()).isEqualTo("italic");
        assertThat(strong.getFirst().getStyleClass()).contains("md-text");
    }

    @Test
    void nestedEmphasisCombinesBothClasses() {
        List<Node> nodes = render("*italic and **both** here*");

        List<Node> both = withClass(nodes, "md-strong");
        assertThat(both).hasSize(1);
        assertThat(both.getFirst().getStyleClass()).contains("md-em", "md-strong");
    }

    @Test
    void inlineCodeIsALabel() {
        List<Node> nodes = render("use `mvn verify` now");

        List<Node> code = withClass(nodes, "md-code-inline");
        assertThat(code).hasSize(1).first().isInstanceOf(Label.class);
        assertThat(((Label) code.getFirst()).getText()).isEqualTo("mvn verify");
    }

    @Test
    void linksCallTheHandlerWithTheirDestination() {
        List<Node> nodes = render("see [the docs](https://example.com/docs) please");

        List<Node> links = withClass(nodes, "md-link");
        assertThat(links).hasSize(1).first().isInstanceOf(Hyperlink.class);
        Hyperlink link = (Hyperlink) links.getFirst();
        assertThat(link.getText()).isEqualTo("the docs");

        link.fire();

        assertThat(clicked).containsExactly("https://example.com/docs");
    }

    @Test
    void imagesRenderAsTheirAltText() {
        List<Node> nodes = render("![a diagram](https://example.com/d.png)");

        assertThat(textOf(nodes.getFirst())).isEqualTo("a diagram");
    }

    @Test
    void bulletListsHaveBulletMarkers() {
        List<Node> nodes = render("- one\n- two");

        assertThat(nodes).hasSize(1);
        assertThat(nodes.getFirst().getStyleClass()).contains("md-list");
        List<Node> markers = withClass(nodes, "md-list-marker");
        assertThat(markers).extracting(node -> ((Label) node).getText()).containsExactly("•", "•");
        assertThat(((VBox) nodes.getFirst()).getChildren()).hasSize(2).allMatch(row -> row instanceof HBox);
    }

    @Test
    void orderedListsAreNumberedFromTheirStart() {
        List<Node> nodes = render("3. three\n4. four");

        assertThat(withClass(nodes, "md-list-marker")).extracting(node -> ((Label) node).getText())
                .containsExactly("3.", "4.");
    }

    @Test
    void nestedListsAreRenderedInsideTheirItem() {
        List<Node> nodes = render("- parent\n  - child\n- sibling");

        List<Node> lists = withClass(nodes, "md-list");
        assertThat(lists).hasSize(2);
        VBox outer = (VBox) nodes.getFirst();
        assertThat(outer.getChildren()).hasSize(2);
        assertThat(withClass(List.of(outer.getChildren().getFirst()), "md-list")).hasSize(1);
        assertThat(textOf(nodes.getFirst())).contains("parent", "child", "sibling");
    }

    @Test
    void fencedAndIndentedCodeBlocksKeepTheirText() {
        List<Node> fenced = render("```java\nint a = 1;\nint b = 2;\n```");
        List<Node> indented = render("    indented code");

        assertThat(fenced.getFirst().getStyleClass()).contains("md-code");
        Label block = (Label) withClass(fenced, "md-code-block").getFirst();
        assertThat(block.getText()).isEqualTo("int a = 1;\nint b = 2;");
        assertThat(block.isWrapText()).isTrue();
        assertThat(((Label) withClass(indented, "md-code-block").getFirst()).getText()).isEqualTo("indented code");
    }

    @Test
    void blockQuotesWrapTheirContent() {
        List<Node> nodes = render("> quoted *text*");

        assertThat(nodes.getFirst().getStyleClass()).contains("md-quote");
        assertThat(textOf(nodes.getFirst())).isEqualTo("quoted text");
        assertThat(withClass(nodes, "md-em")).hasSize(1);
    }

    @Test
    void thematicBreaksBecomeSeparators() {
        List<Node> nodes = render("above\n\n---\n\nbelow");

        assertThat(nodes).hasSize(3);
        assertThat(nodes.get(1)).isInstanceOf(Separator.class);
        assertThat(nodes.get(1).getStyleClass()).contains("md-rule");
    }

    @Test
    void tablesBecomeAGridWithAHeaderRow() {
        List<Node> nodes = render("| Base | Past | Participle |\n|:-----|:----:|-----------:|\n| go | went | gone |\n| see | *saw* | seen |");

        assertThat(nodes).hasSize(1);
        GridPane grid = (GridPane) withClass(nodes, "md-table").getFirst();
        assertThat(grid.getChildren()).hasSize(9);
        List<Node> head = withClass(nodes, "md-table-head");
        assertThat(head).extracting(MarkdownRendererTest::textOf).containsExactly("Base", "Past", "Participle");
        assertThat(withClass(head, "md-strong")).hasSize(3);

        TextFlow went = (TextFlow) grid.getChildren().get(4);
        assertThat(textOf(went)).isEqualTo("went");
        assertThat(GridPane.getRowIndex(went)).isEqualTo(1);
        assertThat(GridPane.getColumnIndex(went)).isEqualTo(1);
        assertThat(went.getTextAlignment()).isEqualTo(TextAlignment.CENTER);
        assertThat(((TextFlow) grid.getChildren().get(5)).getTextAlignment()).isEqualTo(TextAlignment.RIGHT);
        assertThat(withClass(nodes, "md-em")).hasSize(1);
    }

    @Test
    void taskListItemsShowACheckboxInsteadOfABullet() {
        List<Node> nodes = render("- [ ] Unit 2\n- [x] Unit 1\n- plain");

        List<Node> tasks = withClass(nodes, "md-task");
        assertThat(tasks).hasSize(2);
        assertThat(tasks.get(0).getStyleClass()).doesNotContain("md-task-done");
        assertThat(tasks.get(1).getStyleClass()).contains("md-task-done");
        assertThat(withClass(nodes, "md-list-marker")).hasSize(3).allMatch(marker -> marker instanceof Label);
        assertThat(textOf(nodes.getFirst())).isEqualTo("Unit 2Unit 1plain");
    }

    @Test
    void strikethroughGetsItsStyleClass() {
        List<Node> nodes = render("not ~~this~~ one");

        List<Node> struck = withClass(nodes, "md-strike");
        assertThat(struck).hasSize(1);
        assertThat(((Text) struck.getFirst()).getText()).isEqualTo("this");
    }

    @Test
    void underlineAndHighlightGetTheirStyleClasses() {
        List<Node> nodes = render("an ++underlined++ word and a ==highlighted== one, but a == b stays");

        assertThat(((Text) withClass(nodes, "md-underline").getFirst()).getText()).isEqualTo("underlined");
        assertThat(((Text) withClass(nodes, "md-highlight").getFirst()).getText()).isEqualTo("highlighted");
        assertThat(textOf(nodes.getFirst())).isEqualTo("an underlined word and a highlighted one, but a == b stays");
    }

    @Test
    void highlightColorsGetTheirOwnStyleClassAndHideTheirMarker() {
        List<Node> nodes = render("a =={green}fresh== and ==plain== and =={pink}**bold**== and =={nope}x==");

        assertThat(((Text) withClass(nodes, "md-highlight-green").getFirst()).getText()).isEqualTo("fresh");
        assertThat(((Text) withClass(nodes, "md-highlight-yellow").getFirst()).getText()).isEqualTo("plain");
        assertThat(((Text) withClass(nodes, "md-highlight-pink").getFirst()).getText()).isEqualTo("bold");
        assertThat(withClass(nodes, "md-highlight-pink").getFirst().getStyleClass()).contains("md-strong", "md-highlight");
        // an unknown colour is just text inside a yellow highlight
        assertThat(textOf(nodes.getFirst())).isEqualTo("a fresh and plain and bold and {nope}x");
    }

    @Test
    void htmlIsShownAsPlainText() {
        List<Node> block = render("<div>raw</div>");
        List<Node> inline = render("a <b>tag</b> b");

        assertThat(textOf(block.getFirst())).isEqualTo("<div>raw</div>");
        assertThat(textOf(inline.getFirst())).isEqualTo("a <b>tag</b> b");
    }
}
