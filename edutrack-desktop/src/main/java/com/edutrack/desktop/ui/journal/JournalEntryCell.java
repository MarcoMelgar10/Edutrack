package com.edutrack.desktop.ui.journal;

import com.edutrack.application.journal.JournalEntryView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.record.RecordRows;
import com.edutrack.domain.journal.EntryKind;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Callback;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * One journal entry in the list.
 * <pre>
 *  ✎ Shadowing works                                  28 Oct 2026
 *    IELTS 7.0 › Listening practice
 *  ☑ Weekly check-in                       ●●●●○  6.5 h  27 Oct 2026
 * </pre>
 */
final class JournalEntryCell extends ListCell<JournalEntryView> {

    private final JournalViewModel viewModel;

    private JournalEntryCell(JournalViewModel viewModel) {
        this.viewModel = viewModel;
        getStyleClass().addAll("record-cell", "journal-cell");
        setPrefWidth(0);
    }

    static Callback<ListView<JournalEntryView>, ListCell<JournalEntryView>> factory(JournalViewModel viewModel) {
        return listView -> new JournalEntryCell(viewModel);
    }

    @Override
    protected void updateItem(JournalEntryView entry, boolean empty) {
        super.updateItem(entry, empty);
        setText(null);
        if (empty || entry == null) {
            setGraphic(null);
            return;
        }
        boolean checkIn = entry.kind() == EntryKind.CHECK_IN;
        FontIcon icon = new FontIcon(checkIn ? Feather.CHECK_SQUARE : Feather.EDIT_3);
        icon.getStyleClass().add("journal-kind-icon");

        Label headline = new Label(viewModel.headline(entry));
        headline.getStyleClass().add("record-item-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label date = new Label(Formats.date(entry.writtenOn()));
        date.getStyleClass().add("text-caption");
        HBox top = new HBox(8, headline, spacer, date);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(headline, Priority.SOMETIMES);

        Label caption = new Label(viewModel.caption(entry));
        caption.getStyleClass().add("text-caption");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox bottom = new HBox(8, caption, gap);
        bottom.setAlignment(Pos.CENTER_LEFT);
        if (checkIn) {
            ConfidenceDots dots = new ConfidenceDots();
            dots.show(entry.confidence());
            Label hours = new Label(viewModel.weekHours(entry));
            hours.getStyleClass().add("text-caption");
            bottom.getChildren().addAll(dots, hours);
        }

        VBox text = new VBox(2, top, bottom);
        text.getStyleClass().add("record-item-text");
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(10, icon, text);
        row.getStyleClass().add("record-row");
        row.setAlignment(Pos.CENTER_LEFT);
        RecordRows.pinWidth(this, row);
        setGraphic(row);
    }
}
