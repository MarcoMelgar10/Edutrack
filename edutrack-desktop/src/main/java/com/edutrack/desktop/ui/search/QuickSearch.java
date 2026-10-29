package com.edutrack.desktop.ui.search;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.shell.ShellHost;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Ctrl+K from anywhere: a search box over the window with the best hits below it. Up and Down move, Enter
 * opens, Escape or a click outside closes. It lives on the scene root, over the current page.
 */
@Component
public class QuickSearch {

    private static final int LIMIT = 8;

    private final ShellHost host;
    private final ObjectProvider<SearchViewModel> viewModels;
    private final Messages messages;

    private StackPane overlay;

    public QuickSearch(ShellHost host, ObjectProvider<SearchViewModel> viewModels, Messages messages) {
        this.host = host;
        this.viewModels = viewModels;
        this.messages = messages;
    }

    public void toggle() {
        if (overlay != null) {
            close();
        } else {
            open();
        }
    }

    private void open() {
        StackPane root = host.root();
        if (root == null) {
            return;
        }
        SearchViewModel viewModel = viewModels.getObject();
        TextField field = new TextField();
        field.getStyleClass().add("search-field");
        field.setPromptText(messages.get("search.prompt"));
        ListView<SearchViewModel.Row> list = new ListView<>();
        list.getStyleClass().add("quick-search-list");
        list.setFixedCellSize(-1);
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(SearchViewModel.Row row, boolean empty) {
                super.updateItem(row, empty);
                setGraphic(empty || row == null ? null : SearchRows.build(row));
            }
        });
        Label status = new Label();
        status.getStyleClass().add("text-caption");
        status.textProperty().bind(viewModel.statusProperty());

        VBox card = new VBox(8, field, status, list);
        card.getStyleClass().add("quick-search-card");
        card.setMaxWidth(640);
        card.setMaxHeight(520);

        overlay = new StackPane(card);
        overlay.getStyleClass().add("quick-search-overlay");
        overlay.setAlignment(Pos.TOP_CENTER);
        StackPane.setMargin(card, new javafx.geometry.Insets(96, 0, 0, 0));
        overlay.addEventHandler(MouseEvent.MOUSE_PRESSED, press -> {
            if (press.getTarget() == overlay) {
                close();
            }
        });

        field.textProperty().subscribe(text -> {
            viewModel.search(text);
            list.getItems().setAll(viewModel.firstRows(LIMIT));
            list.getSelectionModel().selectFirst();
            list.setPrefHeight(Math.min(list.getItems().size(), LIMIT) * 62 + 4);
        });
        field.setOnKeyPressed(key -> {
            switch (key.getCode()) {
                case ESCAPE -> close();
                case DOWN -> list.getSelectionModel().selectNext();
                case UP -> list.getSelectionModel().selectPrevious();
                case ENTER -> open(viewModel, list);
                default -> { return; }
            }
            key.consume();
        });
        list.setOnMouseClicked(click -> open(viewModel, list));
        list.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ENTER) {
                open(viewModel, list);
            } else if (key.getCode() == KeyCode.ESCAPE) {
                close();
            }
        });

        root.getChildren().add(overlay);
        Platform.runLater(field::requestFocus);
    }

    private void open(SearchViewModel viewModel, ListView<SearchViewModel.Row> list) {
        SearchViewModel.Row row = list.getSelectionModel().getSelectedItem();
        close();
        if (row != null) {
            viewModel.open(row);
        }
    }

    public void close() {
        if (overlay != null && host.root() != null) {
            host.root().getChildren().remove(overlay);
        }
        overlay = null;
    }
}
