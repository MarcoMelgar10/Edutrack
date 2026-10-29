package com.edutrack.desktop.ui.record;

import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;

/**
 * One result — a test, exam or certificate — as a row: title and date/note on the left, the score
 * on the right, edit and delete on hover. Used on the step page and on the goal page.
 * <pre>
 *  IELTS mock test                                   6.5 / 9 · 72%   ✎ 🗑
 *  12 Oct 2026 · Examiner feedback: work on Task 2
 * </pre>
 */
public final class ResultRow {

    private ResultRow() {
    }

    /**
     * @param alsoShowActions extra condition (besides hovering the row) that shows the actions,
     *                        e.g. the owning list cell being selected; may be {@code null}
     */
    public static HBox build(RecordView.ResultView result, Runnable onEdit, Runnable onDelete,
                             ObservableBooleanValue alsoShowActions) {
        Label title = new Label(result.title());
        title.getStyleClass().add("record-item-title");

        String caption = Formats.date(result.takenOn());
        if (result.note() != null && !result.note().isBlank()) {
            caption += " · " + result.note().strip().replaceAll("\\s+", " ");
        }
        Label details = new Label(caption);
        details.getStyleClass().add("text-caption");

        VBox text = new VBox(title, details);
        text.getStyleClass().add("record-item-text");
        HBox.setHgrow(text, Priority.ALWAYS);

        Label score = new Label(Formats.score(result.score(), result.maxScore(), result.percentage()));
        score.getStyleClass().add("result-score");
        score.setMinWidth(Region.USE_PREF_SIZE);

        Button edit = RecordRows.iconButton(Feather.EDIT_2, I18n.get("result.edit"));
        Button delete = RecordRows.iconButton(Feather.TRASH_2, I18n.get("result.delete"));
        edit.setOnAction(click -> onEdit.run());
        delete.setOnAction(click -> onDelete.run());
        HBox actions = new HBox(edit, delete);
        actions.getStyleClass().add("record-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(text, score, actions);
        row.getStyleClass().add("record-row");
        row.setAlignment(Pos.CENTER_LEFT);
        actions.visibleProperty().bind(alsoShowActions == null
                ? row.hoverProperty() : row.hoverProperty().or(alsoShowActions));
        return row;
    }
}
