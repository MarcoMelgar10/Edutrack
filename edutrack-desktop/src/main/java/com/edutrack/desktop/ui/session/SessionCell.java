package com.edutrack.desktop.ui.session;

import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.record.RecordRows;
import com.edutrack.domain.session.SessionSource;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.ZoneId;
import java.util.function.Consumer;

/**
 * One logged session as a row: the source icon (a clock for the timer, a pencil for typed-in time), the date
 * and start time with the step and note under it, the minutes on the right, edit and delete on hover.
 * <pre>
 *  ◷  28 Oct 2026 · 18:05                         42 min   ✎ 🗑
 *     Grammar review · Tenses and articles
 * </pre>
 */
final class SessionCell extends ListCell<SessionView> {

    private final boolean showStep;
    private final ZoneId zone;
    private final Consumer<SessionView> onEdit;
    private final Consumer<SessionView> onDelete;

    private final FontIcon sourceIcon = new FontIcon();
    private final Tooltip sourceTip = new Tooltip();
    private final Label when = new Label();
    private final Label details = new Label();
    private final Label minutes = new Label();
    private final HBox row;

    private SessionCell(boolean showStep, ZoneId zone, Consumer<SessionView> onEdit, Consumer<SessionView> onDelete) {
        this.showStep = showStep;
        this.zone = zone;
        this.onEdit = onEdit;
        this.onDelete = onDelete;
        getStyleClass().add("record-cell");

        Label iconHolder = new Label();
        iconHolder.setGraphic(sourceIcon);
        iconHolder.setTooltip(sourceTip);
        iconHolder.setMinWidth(Region.USE_PREF_SIZE);

        when.getStyleClass().add("record-item-title");
        details.getStyleClass().add("text-caption");
        details.setWrapText(true);
        VBox text = new VBox(when, details);
        text.getStyleClass().add("record-item-text");
        HBox.setHgrow(text, Priority.ALWAYS);

        minutes.getStyleClass().add("session-minutes");
        minutes.setMinWidth(Region.USE_PREF_SIZE);

        Button edit = RecordRows.iconButton(Feather.EDIT_2, I18n.get("session.edit"));
        Button delete = RecordRows.iconButton(Feather.TRASH_2, I18n.get("session.delete"));
        edit.setOnAction(click -> this.onEdit.accept(getItem()));
        delete.setOnAction(click -> this.onDelete.accept(getItem()));
        HBox actions = new HBox(edit, delete);
        actions.getStyleClass().add("record-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        row = new HBox(iconHolder, text, minutes, actions);
        row.getStyleClass().add("record-row");
        row.setAlignment(Pos.CENTER_LEFT);
        actions.visibleProperty().bind(row.hoverProperty().or(selectedProperty()));
        RecordRows.pinWidth(this, row);
    }

    /**
     * @param showStep whether each row names its step (the list of a whole goal does; the list of one step does not)
     */
    static Callback<ListView<SessionView>, ListCell<SessionView>> factory(
            boolean showStep, ZoneId zone, Consumer<SessionView> onEdit, Consumer<SessionView> onDelete) {
        return listView -> new SessionCell(showStep, zone, onEdit, onDelete);
    }

    @Override
    protected void updateItem(SessionView session, boolean empty) {
        super.updateItem(session, empty);
        setText(null);
        if (empty || session == null) {
            setGraphic(null);
            return;
        }
        boolean timed = session.source() == SessionSource.TIMER;
        sourceIcon.setIconCode(timed ? Feather.CLOCK : Feather.EDIT_3);
        sourceTip.setText(I18n.get(timed ? "session.source.timer" : "session.source.manual"));

        when.setText(Formats.date(session.startedAt(), zone) + " · " + Formats.time(session.startedAt(), zone));
        StringBuilder caption = new StringBuilder();
        if (showStep && session.stepTitle() != null) {
            caption.append(session.stepTitle());
        }
        if (session.note() != null && !session.note().isBlank()) {
            if (!caption.isEmpty()) {
                caption.append(" · ");
            }
            caption.append(session.note().strip().replaceAll("\\s+", " "));
        }
        details.setText(caption.toString());
        details.setVisible(!caption.isEmpty());
        details.setManaged(!caption.isEmpty());
        minutes.setText(SessionFormats.duration(session.minutes()));
        setGraphic(row);
    }
}
