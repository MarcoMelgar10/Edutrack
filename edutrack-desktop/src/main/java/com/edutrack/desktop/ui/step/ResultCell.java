package com.edutrack.desktop.ui.step;

import com.edutrack.application.record.RecordView.ResultView;
import com.edutrack.desktop.ui.record.RecordRows;
import com.edutrack.desktop.ui.record.ResultRow;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.util.function.Consumer;

/** One result of the step, drawn by {@link ResultRow}. */
final class ResultCell extends ListCell<ResultView> {

    private final Consumer<ResultView> onEdit;
    private final Consumer<ResultView> onDelete;

    ResultCell(Consumer<ResultView> onEdit, Consumer<ResultView> onDelete) {
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        getStyleClass().add("record-cell");
        setPrefWidth(0);
    }

    static Callback<ListView<ResultView>, ListCell<ResultView>> factory(
            Consumer<ResultView> onEdit, Consumer<ResultView> onDelete) {
        return listView -> new ResultCell(onEdit, onDelete);
    }

    @Override
    protected void updateItem(ResultView result, boolean empty) {
        super.updateItem(result, empty);
        setText(null);
        if (empty || result == null) {
            setGraphic(null);
            return;
        }
        HBox row = ResultRow.build(result, () -> onEdit.accept(result), () -> onDelete.accept(result),
                selectedProperty());
        RecordRows.pinWidth(this, row);
        setGraphic(row);
    }
}
