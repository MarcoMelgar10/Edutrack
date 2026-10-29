package com.edutrack.desktop.ui.step;

import com.edutrack.application.record.RecordView.ExerciseView;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.record.RecordRows;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import org.kordamp.ikonli.feather.Feather;

/** One exercise of the step: a check box with its title; move, edit and delete on hover. */
final class ExerciseCell extends ListCell<ExerciseView> {

    /** What the cell can ask its owner to do. */
    interface Actions {
        void markDone(ExerciseView exercise, boolean done);

        void moveUp(ExerciseView exercise);

        void moveDown(ExerciseView exercise);

        void edit(ExerciseView exercise);

        void delete(ExerciseView exercise);
    }

    private final CheckBox done = new CheckBox();
    private final Button up = RecordRows.iconButton(Feather.CHEVRON_UP, I18n.get("action.moveUp"));
    private final Button down = RecordRows.iconButton(Feather.CHEVRON_DOWN, I18n.get("action.moveDown"));
    private final HBox row;

    ExerciseCell(Actions actions) {
        getStyleClass().add("record-cell");

        done.getStyleClass().add("exercise-check");
        done.setMaxWidth(Double.MAX_VALUE);
        done.setMnemonicParsing(false);
        HBox.setHgrow(done, Priority.ALWAYS);

        Button edit = RecordRows.iconButton(Feather.EDIT_2, I18n.get("exercise.edit"));
        Button delete = RecordRows.iconButton(Feather.TRASH_2, I18n.get("exercise.delete"));
        HBox rowActions = new HBox(up, down, edit, delete);
        rowActions.getStyleClass().add("record-actions");
        rowActions.setAlignment(Pos.CENTER_RIGHT);
        rowActions.visibleProperty().bind(hoverProperty().or(selectedProperty()));

        row = new HBox(done, rowActions);
        row.getStyleClass().add("record-row");
        row.setAlignment(Pos.CENTER_LEFT);

        done.setOnAction(click -> actions.markDone(getItem(), done.isSelected()));
        up.setOnAction(click -> actions.moveUp(getItem()));
        down.setOnAction(click -> actions.moveDown(getItem()));
        edit.setOnAction(click -> actions.edit(getItem()));
        delete.setOnAction(click -> actions.delete(getItem()));

        RecordRows.pinWidth(this, row);
    }

    static Callback<ListView<ExerciseView>, ListCell<ExerciseView>> factory(Actions actions) {
        return listView -> new ExerciseCell(actions);
    }

    @Override
    protected void updateItem(ExerciseView exercise, boolean empty) {
        super.updateItem(exercise, empty);
        getStyleClass().remove("done");
        setText(null);
        if (empty || exercise == null) {
            setGraphic(null);
            return;
        }
        done.setText(exercise.title());
        done.setSelected(exercise.done());
        if (exercise.done()) {
            getStyleClass().add("done");
        }
        int last = getListView().getItems().size() - 1;
        up.setDisable(getIndex() == 0);
        down.setDisable(getIndex() == last);
        setGraphic(row);
    }
}
