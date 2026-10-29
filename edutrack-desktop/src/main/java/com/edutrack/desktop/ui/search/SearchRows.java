package com.edutrack.desktop.ui.search;

import com.edutrack.application.search.SearchKind;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/** Draws a search hit the same way on the search page and in the quick-search box. */
final class SearchRows {

    private SearchRows() {
    }

    static Feather icon(SearchKind kind) {
        return switch (kind) {
            case GOAL -> Feather.TARGET;
            case STEP -> Feather.FLAG;
            case NOTE -> Feather.FILE_TEXT;
            case RESOURCE -> Feather.LINK;
            case RESULT -> Feather.AWARD;
            case CARD -> Feather.LAYERS;
            case JOURNAL -> Feather.BOOK_OPEN;
            case EVIDENCE -> Feather.PAPERCLIP;
            case COMPETENCY -> Feather.BAR_CHART_2;
        };
    }

    /** Icon, title, where it lives and the excerpt with the matched words in bold. */
    static Node build(SearchViewModel.Row row) {
        FontIcon icon = new FontIcon(icon(row.hit().kind()));
        icon.getStyleClass().add("routine-mark");

        Label title = new Label(row.title());
        title.getStyleClass().add("routine-title");
        VBox text = new VBox(2, title);
        if (!row.location().isEmpty()) {
            Label location = new Label(row.location());
            location.getStyleClass().add("text-caption");
            text.getChildren().add(location);
        }
        if (!row.snippet().isEmpty()) {
            TextFlow snippet = new TextFlow();
            snippet.getStyleClass().add("search-snippet");
            for (SearchViewModel.Piece piece : row.snippet()) {
                Text part = new Text(piece.text());
                part.getStyleClass().add(piece.bold() ? "search-hit-text" : "search-text");
                snippet.getChildren().add(part);
            }
            text.getChildren().add(snippet);
        }
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox box = new HBox(12, icon, text);
        box.setAlignment(Pos.TOP_LEFT);
        box.getStyleClass().add("search-row");
        return box;
    }
}
