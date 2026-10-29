package com.edutrack.desktop.ui.markdown;

import javafx.scene.layout.VBox;

import java.util.function.Consumer;

/**
 * Read-only rendering of Markdown as JavaFX nodes (headings, paragraphs, emphasis, inline code,
 * links, lists, quotes, code blocks, rules). Call {@link #setMarkdown(String)} whenever the text changes.
 */
public final class MarkdownView extends VBox {

    private Consumer<String> onLink = destination -> { };
    private String markdown = "";

    public MarkdownView() {
        getStyleClass().add("markdown-view");
    }

    /** What to do when a link is clicked; receives the link destination. */
    public void setOnLink(Consumer<String> onLink) {
        this.onLink = onLink == null ? destination -> { } : onLink;
    }

    public void setMarkdown(String markdown) {
        this.markdown = markdown == null ? "" : markdown;
        getChildren().setAll(new MarkdownRenderer(destination -> onLink.accept(destination)).render(this.markdown));
    }

    public String getMarkdown() {
        return markdown;
    }
}
