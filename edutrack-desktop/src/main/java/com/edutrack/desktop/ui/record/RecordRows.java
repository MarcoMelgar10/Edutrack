package com.edutrack.desktop.ui.record;

import javafx.beans.binding.Bindings;
import javafx.scene.control.Button;
import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.javafx.FontIcon;

/** Small helpers shared by the rows of resources, exercises and results. */
public final class RecordRows {

    private RecordRows() {
    }

    /**
     * Lays {@code row} out at the cell's width, not its preferred width: long titles end in "…"
     * instead of widening the list, and the actions stay aligned at the right edge.
     */
    public static void pinWidth(ListCell<?> cell, Region row) {
        cell.setPrefWidth(0);
        row.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> cell.getWidth() - cell.getPadding().getLeft() - cell.getPadding().getRight(),
                cell.widthProperty(), cell.paddingProperty()));
    }

    /** A quiet icon-only button for the actions at the end of a row. */
    public static Button iconButton(Ikon icon, String tooltip) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }
}
