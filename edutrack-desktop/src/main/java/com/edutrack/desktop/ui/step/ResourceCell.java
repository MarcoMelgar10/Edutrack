package com.edutrack.desktop.ui.step;

import com.edutrack.application.record.RecordView.ResourceView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.record.RecordRows;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.kordamp.ikonli.feather.Feather;

/**
 * One resource of the step: a "reviewed" check box, the title, its type and link, and edit/delete on hover.
 * <pre>
 *  ☑  Cambridge Grammar in Use                          ✎ 🗑
 *     Book  bbc.co.uk/learningenglish
 * </pre>
 */
final class ResourceCell extends ListCell<ResourceView> {

    /** What the cell can ask its owner to do. */
    interface Actions {
        void markReviewed(ResourceView resource, boolean reviewed);

        void openLink(String url);

        void edit(ResourceView resource);

        void delete(ResourceView resource);
    }

    private final CheckBox reviewed = new CheckBox();
    private final Label title = new Label();
    private final Label type = new Label();
    private final Hyperlink link = new Hyperlink();
    private final HBox row;

    ResourceCell(Actions actions) {
        getStyleClass().add("record-cell");

        reviewed.getStyleClass().add("reviewed-check");
        reviewed.setTooltip(new Tooltip(I18n.get("resource.reviewed")));
        reviewed.setAccessibleText(I18n.get("resource.reviewed"));
        title.getStyleClass().add("record-item-title");
        type.getStyleClass().add("record-type");
        link.getStyleClass().add("record-link");
        link.setFocusTraversable(false);
        HBox meta = new HBox(8, type, link);
        meta.setAlignment(Pos.CENTER_LEFT);
        VBox text = new VBox(title, meta);
        text.getStyleClass().add("record-item-text");
        HBox.setHgrow(text, Priority.ALWAYS);

        Button edit = RecordRows.iconButton(Feather.EDIT_2, I18n.get("resource.edit"));
        Button delete = RecordRows.iconButton(Feather.TRASH_2, I18n.get("resource.delete"));
        HBox rowActions = new HBox(edit, delete);
        rowActions.getStyleClass().add("record-actions");
        rowActions.setAlignment(Pos.CENTER_RIGHT);
        rowActions.visibleProperty().bind(hoverProperty().or(selectedProperty()));

        row = new HBox(reviewed, text, rowActions);
        row.getStyleClass().add("record-row");
        row.setAlignment(Pos.CENTER_LEFT);

        reviewed.setOnAction(click -> actions.markReviewed(getItem(), reviewed.isSelected()));
        link.setOnAction(click -> actions.openLink(getItem().url()));
        edit.setOnAction(click -> actions.edit(getItem()));
        delete.setOnAction(click -> actions.delete(getItem()));

        RecordRows.pinWidth(this, row);
    }

    static Callback<ListView<ResourceView>, ListCell<ResourceView>> factory(Actions actions) {
        return listView -> new ResourceCell(actions);
    }

    @Override
    protected void updateItem(ResourceView resource, boolean empty) {
        super.updateItem(resource, empty);
        getStyleClass().remove("done");
        setText(null);
        if (empty || resource == null) {
            setGraphic(null);
            return;
        }
        reviewed.setSelected(resource.reviewed());
        title.setText(resource.title());
        type.setText(Formats.resourceType(resource.type()));
        boolean hasLink = resource.url() != null && !resource.url().isBlank();
        link.setText(hasLink ? Formats.link(resource.url()) : "");
        link.setVisible(hasLink);
        link.setManaged(hasLink);
        if (resource.reviewed()) {
            getStyleClass().add("done");
        }
        setGraphic(row);
    }
}
