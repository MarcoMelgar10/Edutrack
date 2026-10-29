package com.edutrack.desktop.ui.cards;

import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.markdown.MarkdownView;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A multi-line text area for Markdown with a small "Preview" toggle under it that swaps the editor for
 * the rendered text. Used for the two sides of a card, where seeing the formatting matters.
 */
public final class MarkdownField extends VBox {

    private final TextArea area = new TextArea();
    private final MarkdownView preview = new MarkdownView();
    private final ScrollPane previewScroll = new ScrollPane(preview);
    private final ToggleButton previewToggle = new ToggleButton();

    public MarkdownField() {
        getStyleClass().add("markdown-field");
        setSpacing(4);

        area.setWrapText(true);
        area.setPrefRowCount(4);
        area.getStyleClass().add("markdown-field-editor");

        previewScroll.setFitToWidth(true);
        previewScroll.getStyleClass().add("markdown-field-preview");
        previewScroll.setVisible(false);
        previewScroll.prefHeightProperty().bind(area.heightProperty());
        previewScroll.minHeightProperty().bind(area.heightProperty());
        previewScroll.maxHeightProperty().bind(area.heightProperty());

        previewToggle.setGraphic(new FontIcon(Feather.EYE));
        previewToggle.setText(I18n.get("cards.preview"));
        previewToggle.getStyleClass().add("flat");
        previewToggle.setTooltip(new Tooltip(I18n.get("cards.preview.tooltip")));
        previewToggle.setFocusTraversable(true);
        previewToggle.selectedProperty().subscribe(showing -> {
            if (showing) {
                preview.setMarkdown(area.getText());
            }
            previewScroll.setVisible(showing);
            area.setVisible(!showing);
        });

        HBox bar = new HBox(previewToggle);
        bar.setAlignment(Pos.CENTER_RIGHT);
        getChildren().addAll(new StackPane(area, previewScroll), bar);
    }

    public StringProperty textProperty() {
        return area.textProperty();
    }

    public String getText() {
        return area.getText();
    }

    public void setText(String text) {
        area.setText(text);
    }

    public String getPromptText() {
        return area.getPromptText();
    }

    public void setPromptText(String promptText) {
        area.setPromptText(promptText);
    }

    /** How many lines the editor shows without scrolling. */
    public void setRows(int rows) {
        area.setPrefRowCount(rows);
    }

    public int getRows() {
        return area.getPrefRowCount();
    }

    /** Puts the cursor in the editor. */
    public void focusEditor() {
        area.requestFocus();
    }
}
