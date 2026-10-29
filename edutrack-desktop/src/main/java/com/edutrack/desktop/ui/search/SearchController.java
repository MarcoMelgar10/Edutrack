package com.edutrack.desktop.ui.search;

import com.edutrack.desktop.ui.shell.PageLifecycle;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The Search page: a box that searches as you type and lists what it finds in one section per kind (goals,
 * steps, notes, resources, results, cards, journal, evidence, competencies). A click opens the hit.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SearchController implements PageLifecycle {

    private static final Duration DEBOUNCE = Duration.millis(250);

    private final SearchViewModel viewModel;
    private final PauseTransition pause = new PauseTransition(DEBOUNCE);

    @FXML
    private TextField queryField;
    @FXML
    private Label statusLabel;
    @FXML
    private VBox results;

    public SearchController(SearchViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        statusLabel.textProperty().bind(viewModel.statusProperty());
        pause.setOnFinished(finished -> viewModel.search(queryField.getText()));
        queryField.textProperty().subscribe(text -> pause.playFromStart());
        queryField.setOnAction(action -> {
            pause.stop();
            viewModel.search(queryField.getText());
        });
        viewModel.groups().addListener((ListChangeListener<SearchViewModel.Group>) change -> render());
    }

    @Override
    public void shown() {
        Platform.runLater(queryField::requestFocus);
        if (!queryField.getText().isBlank()) {
            viewModel.search(queryField.getText()); // what was found may have changed since
        }
    }

    private void render() {
        results.getChildren().clear();
        for (SearchViewModel.Group group : viewModel.groups()) {
            Label heading = new Label(group.heading());
            heading.getStyleClass().add("section-label");
            VBox rows = new VBox();
            rows.getStyleClass().add("area-list");
            group.rows().forEach(row -> rows.getChildren().add(clickable(row)));
            VBox section = new VBox(6, heading, rows);
            results.getChildren().add(section);
        }
    }

    private Node clickable(SearchViewModel.Row row) {
        Node node = SearchRows.build(row);
        node.setCursor(Cursor.HAND);
        node.setFocusTraversable(true);
        node.setAccessibleText(row.title() + ", " + row.location());
        node.setOnMouseClicked(click -> viewModel.open(row));
        node.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ENTER || key.getCode() == KeyCode.SPACE) {
                viewModel.open(row);
                key.consume();
            }
        });
        if (node instanceof javafx.scene.layout.HBox box) {
            box.setAlignment(Pos.TOP_LEFT);
        }
        return node;
    }
}
