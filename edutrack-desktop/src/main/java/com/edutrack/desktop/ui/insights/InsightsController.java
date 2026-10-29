package com.edutrack.desktop.ui.insights;

import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.common.AreaIcons;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.desktop.ui.shell.PageLifecycle;
import javafx.collections.ListChangeListener;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Binds {@code InsightsView.fxml} to {@link InsightsViewModel}: one clickable row per area, which opens the area.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class InsightsController implements PageLifecycle {

    private final InsightsViewModel viewModel;
    private final Navigator navigator;

    @FXML
    private VBox chartsHost;
    @FXML
    private VBox areaList;
    @FXML
    private Label emptyLabel;

    public InsightsController(InsightsViewModel viewModel, Navigator navigator) {
        this.viewModel = viewModel;
        this.navigator = navigator;
    }

    @FXML
    private void initialize() {
        chartsHost.getChildren().setAll(new InsightsCharts(viewModel).node());
        chartsHost.visibleProperty().bind(viewModel.emptyProperty().not());
        chartsHost.managedProperty().bind(chartsHost.visibleProperty());
        emptyLabel.visibleProperty().bind(viewModel.emptyProperty());
        emptyLabel.managedProperty().bind(emptyLabel.visibleProperty());
        areaList.visibleProperty().bind(viewModel.emptyProperty().not());
        areaList.managedProperty().bind(areaList.visibleProperty());

        viewModel.areas().addListener((ListChangeListener<AreaSummary>) change -> rebuildRows());
        rebuildRows();
    }

    @Override
    public void disposed() {
        viewModel.dispose();
    }

    private void rebuildRows() {
        areaList.getChildren().setAll(viewModel.areas().stream().map(this::row).toList());
    }

    private Node row(AreaSummary area) {
        Label name = new Label(area.name());
        name.getStyleClass().add("area-row-name");
        Label goals = new Label(goalCount(area.goalCount()));
        goals.getStyleClass().add("text-caption");
        VBox text = new VBox(2, name, goals);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        ProgressBar bar = new ProgressBar(area.progress().fraction());
        bar.getStyleClass().add("area-progress");
        bar.setPrefWidth(220);

        Label percent = new Label(Formats.percent(area.progress()));
        percent.getStyleClass().add("progress-value");
        percent.setMinWidth(44);
        percent.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, AreaIcons.icon(area.icon()), text, spacer, bar, percent);
        row.getStyleClass().add("area-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);

        // Rows act as buttons: reachable with Tab, opened with Enter or Space.
        row.setFocusTraversable(true);
        row.setAccessibleText(area.name() + ", " + goalCount(area.goalCount()) + ", " + Formats.percent(area.progress()));
        row.setOnMouseClicked(click -> navigator.openArea(area.id()));
        row.setOnKeyPressed(key -> {
            if (key.getCode() == KeyCode.ENTER || key.getCode() == KeyCode.SPACE) {
                navigator.openArea(area.id());
                key.consume();
            }
        });
        return row;
    }

    private static String goalCount(int count) {
        return count == 0 ? I18n.get("insights.goals.none") : I18n.plural("count.goals", count);
    }
}
