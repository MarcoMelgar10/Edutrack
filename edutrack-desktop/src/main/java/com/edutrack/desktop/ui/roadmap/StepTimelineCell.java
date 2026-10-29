package com.edutrack.desktop.ui.roadmap;

import com.edutrack.application.goal.StepView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.domain.goal.StepStatus;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;

/**
 * One roadmap step drawn as a stop on a vertical timeline:
 * <pre>
 *  │
 *  ◐── 2. Listening practice                    In progress   20h   ▲ ▼ ✎ 🗑
 *  │     Podcasts and BBC Learning English
 *  │     Notes · 3 resources · Exercises 2/5
 * </pre>
 * The rail segments above and below the mark join across cells (cells have no vertical padding),
 * and are hidden before the first and after the last step. The title opens the step page;
 * row actions appear on hover or selection.
 */
public final class StepTimelineCell extends ListCell<StepView> {

    /** What the cell can ask its owner to do. */
    public interface Actions {
        void cycleStatus(StepView step);

        /** Opens the step page (notes, resources, exercises, results). */
        void open(StepView step);

        void moveUp(StepView step);

        void moveDown(StepView step);

        void edit(StepView step);

        void delete(StepView step);
    }

    private static final Map<StepStatus, String> NEXT_ACTION_KEY = Map.of(
            StepStatus.NOT_STARTED, "step.markInProgress",
            StepStatus.IN_PROGRESS, "step.markDone",
            StepStatus.DONE, "step.markNotStarted");

    private final Region railAbove = rail("rail-above");
    private final Region railBelow = rail("rail-below");
    private final StepMark mark = new StepMark();
    private final Button markButton = new Button();
    private final Label title = new Label();
    private final Label description = new Label();
    private final Label record = new Label();
    private final Label status = new Label();
    private final Label hours = new Label();
    private final Button up = iconButton(Feather.CHEVRON_UP, I18n.get("action.moveUp"));
    private final Button down = iconButton(Feather.CHEVRON_DOWN, I18n.get("action.moveDown"));
    private final Button edit = iconButton(Feather.EDIT_2, I18n.get("step.edit"));
    private final Button delete = iconButton(Feather.TRASH_2, I18n.get("step.delete"));
    private final HBox row;

    public StepTimelineCell(Actions actions) {
        getStyleClass().add("timeline-cell");

        markButton.setGraphic(mark);
        markButton.getStyleClass().add("step-mark-button");
        markButton.setTooltip(new Tooltip());
        VBox railColumn = new VBox(railAbove, markButton, railBelow);
        railColumn.getStyleClass().add("rail-column");
        railColumn.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(railBelow, Priority.ALWAYS);

        title.getStyleClass().addAll("step-title", "step-title-link");
        title.setTooltip(new Tooltip(I18n.get("step.openTooltip")));
        description.getStyleClass().addAll("step-description", "text-caption");
        description.setWrapText(true);
        record.getStyleClass().addAll("step-record", "text-caption");
        VBox content = new VBox(title, description, record);
        content.getStyleClass().add("step-content");
        HBox.setHgrow(content, Priority.ALWAYS);

        status.getStyleClass().addAll("step-status", "text-caption");
        hours.getStyleClass().add("step-hours");
        HBox meta = new HBox(status, hours);
        meta.getStyleClass().add("step-meta");
        meta.setAlignment(Pos.TOP_RIGHT);

        HBox rowActions = new HBox(up, down, edit, delete);
        rowActions.getStyleClass().add("step-actions");
        rowActions.setAlignment(Pos.TOP_RIGHT);
        rowActions.visibleProperty().bind(hoverProperty().or(selectedProperty()));

        row = new HBox(railColumn, content, meta, rowActions);
        row.getStyleClass().add("timeline-row");

        markButton.setOnAction(click -> actions.cycleStatus(getItem()));
        title.setOnMouseClicked(click -> {
            if (click.getButton() == MouseButton.PRIMARY && click.getClickCount() == 1) {
                actions.open(getItem());
                click.consume();
            }
        });
        up.setOnAction(click -> actions.moveUp(getItem()));
        down.setOnAction(click -> actions.moveDown(getItem()));
        edit.setOnAction(click -> actions.edit(getItem()));
        delete.setOnAction(click -> actions.delete(getItem()));
        setOnMouseClicked(click -> {
            if (click.getButton() == MouseButton.PRIMARY && click.getClickCount() == 2 && !isEmpty()) {
                actions.open(getItem());
            }
        });

        // Lay the row out at the cell's width, not its preferred width: long titles end in "…" instead
        // of widening the list, and status, hours and actions stay aligned at the right edge.
        setPrefWidth(0);
        row.prefWidthProperty().bind(Bindings.createDoubleBinding(
                () -> getWidth() - getPadding().getLeft() - getPadding().getRight(),
                widthProperty(), paddingProperty()));
    }

    /** Wraps the cell as a {@link ListView} cell factory. */
    public static javafx.util.Callback<ListView<StepView>, ListCell<StepView>> factory(Actions actions) {
        return listView -> new StepTimelineCell(actions);
    }

    @Override
    protected void updateItem(StepView step, boolean empty) {
        super.updateItem(step, empty);
        getStyleClass().remove("done");
        setText(null);
        if (empty || step == null) {
            setGraphic(null);
            return;
        }

        int last = getListView().getItems().size() - 1;
        railAbove.setVisible(getIndex() > 0);
        railBelow.setVisible(getIndex() < last);

        mark.show(step.status());
        String nextAction = I18n.get(NEXT_ACTION_KEY.get(step.status()));
        markButton.getTooltip().setText(nextAction);
        markButton.setAccessibleText(Formats.status(step.status()) + ". " + nextAction);
        if (step.status() == StepStatus.DONE) {
            getStyleClass().add("done");
        }

        title.setText(step.number() + ". " + step.title());
        showIfPresent(description, step.description());
        showIfPresent(record, Formats.recordSummary(step.record()));
        status.setText(Formats.status(step.status()));
        hours.setText(Formats.hours(step.estimatedHours()));

        up.setDisable(getIndex() == 0);
        down.setDisable(getIndex() == last);
        setGraphic(row);
    }

    private static void showIfPresent(Label label, String text) {
        boolean present = text != null && !text.isBlank();
        label.setText(present ? text : "");
        label.setVisible(present);
        label.setManaged(present);
    }

    private static Region rail(String styleClass) {
        Region segment = new Region();
        segment.getStyleClass().addAll("rail", styleClass);
        return segment;
    }

    private static Button iconButton(Ikon icon, String tooltip) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tooltip));
        button.setAccessibleText(tooltip);
        return button;
    }
}
