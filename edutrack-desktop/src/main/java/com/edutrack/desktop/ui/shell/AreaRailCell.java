package com.edutrack.desktop.ui.shell;

import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.common.AreaIcons;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Reorderable;
import com.edutrack.desktop.ui.record.RecordRows;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * One area in the left rail: its icon, its name and its progress in muted rounded percent. The
 * selected row (the area being shown) is highlighted by the stylesheet. The context menu edits,
 * reorders and deletes the area; the row can also be dragged to a new place.
 */
final class AreaRailCell extends ListCell<AreaSummary> {

    /** What the cell can ask its owner to do. */
    interface Actions extends Reorderable.Mover<AreaSummary> {
        void edit(AreaSummary area);

        void delete(AreaSummary area);
    }

    private final HBox row;
    private final Label name = new Label();
    private final Label percent = new Label();
    private final MenuItem moveUp = new MenuItem(I18n.get("action.moveUp"));
    private final MenuItem moveDown = new MenuItem(I18n.get("action.moveDown"));
    private final ContextMenu menu;

    AreaRailCell(Actions actions) {
        getStyleClass().add("area-rail-cell");

        name.getStyleClass().add("area-rail-name");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(name, Priority.ALWAYS);
        percent.getStyleClass().add("area-rail-percent");
        percent.setMinWidth(Region.USE_PREF_SIZE);

        row = new HBox(10, new FontIcon(), name, percent); // the icon is swapped in updateItem
        row.getStyleClass().add("area-rail-row");
        row.setAlignment(Pos.CENTER_LEFT);

        MenuItem edit = new MenuItem(I18n.get("area.edit"));
        edit.setOnAction(click -> actions.edit(getItem()));
        MenuItem delete = new MenuItem(I18n.get("area.delete"));
        delete.setOnAction(click -> actions.delete(getItem()));
        moveUp.setOnAction(click -> actions.step(getItem(), -1));
        moveDown.setOnAction(click -> actions.step(getItem(), 1));
        menu = new ContextMenu(edit, new SeparatorMenuItem(), moveUp, moveDown, new SeparatorMenuItem(), delete);

        RecordRows.pinWidth(this, row);
        Reorderable.install(this, actions);
    }

    @Override
    protected void updateItem(AreaSummary area, boolean empty) {
        super.updateItem(area, empty);
        setText(null);
        if (empty || area == null) {
            setGraphic(null);
            setContextMenu(null);
            return;
        }
        FontIcon next = AreaIcons.icon(area.icon());
        row.getChildren().set(0, next);
        name.setText(area.name());
        percent.setText(Formats.percent(area.progress()));
        setAccessibleText(area.name() + ", " + Formats.percent(area.progress()));
        moveUp.setDisable(getIndex() == 0);
        moveDown.setDisable(getIndex() == getListView().getItems().size() - 1);
        setContextMenu(menu);
        setGraphic(row);
    }
}
