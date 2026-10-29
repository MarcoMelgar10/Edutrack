package com.edutrack.desktop.ui.insights;

import com.edutrack.desktop.ui.common.I18n;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;

/**
 * The charts of the Insights page, drawn from {@link InsightsViewModel}: study time per week, the study-days
 * calendar, a goal's progress over time and one trend per measure. Colors come from the style sheet (accent
 * and muted greys only), so a chart looks right in both themes.
 */
final class InsightsCharts {

    private final InsightsViewModel viewModel;
    private final VBox root = new VBox(24);
    private final VBox weekBox = new VBox(8);
    private final VBox heatBox = new VBox(8);
    private final VBox goalBox = new VBox(12);
    // Created once and moved between the rebuilt sections, so their bindings are made only once.
    private final ComboBox<InsightsViewModel.Range> rangeBox = new ComboBox<>();
    private final ComboBox<InsightsViewModel.AreaChoice> areaBox = new ComboBox<>();
    private final ComboBox<InsightsViewModel.GoalChoice> goalsBox = new ComboBox<>();

    InsightsCharts(InsightsViewModel viewModel) {
        this.viewModel = viewModel;
        root.getStyleClass().add("insights-charts");
        root.getChildren().addAll(weekBox, heatBox, goalBox);
        rangeBox.setItems(viewModel.ranges());
        rangeBox.valueProperty().bindBidirectional(viewModel.rangeProperty());
        areaBox.setItems(viewModel.areaChoices());
        areaBox.valueProperty().bindBidirectional(viewModel.areaFilterProperty());
        goalsBox.setItems(viewModel.goalChoices());
        goalsBox.valueProperty().bindBidirectional(viewModel.goalProperty());
        goalsBox.setMaxWidth(320);
        buildWeeks();
        buildHeat();
        buildGoal();
        viewModel.weekBars().addListener((ListChangeListener<InsightsViewModel.WeekBar>) change -> buildWeeks());
        viewModel.heatProperty().subscribe(heat -> buildHeat());
        viewModel.progressPoints().addListener((ListChangeListener<InsightsViewModel.LinePoint>) change -> buildGoal());
        viewModel.measureLines().addListener((ListChangeListener<InsightsViewModel.MeasureLine>) change -> buildGoal());
    }

    Node node() {
        return root;
    }

    // ---- study time per week ----

    private void buildWeeks() {
        weekBox.getChildren().setAll(sectionHeader(I18n.get("insights.week.title"), filters()));
        Label summary = new Label(viewModel.weekSummaryProperty().get());
        summary.getStyleClass().add("text-caption");
        weekBox.getChildren().add(summary);

        CategoryAxis x = new CategoryAxis();
        x.setTickLabelRotation(0);
        NumberAxis y = new NumberAxis();
        y.setLabel(I18n.get("insights.week.axis"));
        y.setMinorTickVisible(false);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.getStyleClass().add("insights-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCategoryGap(6);
        chart.setMinHeight(220);
        chart.setPrefHeight(240);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        List<InsightsViewModel.WeekBar> bars = viewModel.weekBars();
        for (int index = 0; index < bars.size(); index++) {
            InsightsViewModel.WeekBar bar = bars.get(index);
            // Category names must be unique; the label repeats rarely, so make it unique with the index when needed.
            XYChart.Data<String, Number> data = new XYChart.Data<>(uniqueLabel(bars, index), bar.hours());
            data.nodeProperty().subscribe(node -> {
                if (node != null) {
                    if (bar.current()) {
                        addClassLater(node, "bar-current");
                    }
                    Tooltip.install(node, new Tooltip(bar.tooltip()));
                }
            });
            series.getData().add(data);
        }
        chart.getData().add(series);
        weekBox.getChildren().add(chart);
    }

    private static String uniqueLabel(List<InsightsViewModel.WeekBar> bars, int index) {
        String label = bars.get(index).label();
        long before = bars.subList(0, index).stream().filter(other -> other.label().equals(label)).count();
        return before == 0 ? label : label + " ".repeat((int) before);
    }

    private Node filters() {
        HBox box = new HBox(8, areaBox, rangeBox);
        box.setAlignment(Pos.CENTER_RIGHT);
        return box;
    }

    // ---- study days ----

    private void buildHeat() {
        heatBox.getChildren().setAll(sectionHeader(I18n.get("insights.heat.title"), null));
        Label streak = new Label(viewModel.streakTextProperty().get());
        streak.getStyleClass().add("text-caption");
        heatBox.getChildren().add(streak);
        InsightsViewModel.Heat heat = viewModel.heatProperty().get();
        if (heat != null) {
            heatBox.getChildren().add(new Heatmap(heat, viewModel.weekStart(), viewModel.today()));
        }
    }

    // ---- one goal ----

    private void buildGoal() {
        goalBox.getChildren().clear();
        if (viewModel.goalChoices().isEmpty()) {
            return;
        }
        goalBox.getChildren().add(sectionHeader(I18n.get("insights.goal.title"), goalsBox));

        List<InsightsViewModel.LinePoint> progress = viewModel.progressPoints();
        if (progress.size() >= 2) {
            goalBox.getChildren().add(line(progress, null, 0.0, 100.0, "%", I18n.get("insights.goal.axis")));
        } else {
            Label none = new Label(I18n.get("insights.goal.none"));
            none.getStyleClass().add("text-caption");
            goalBox.getChildren().add(none);
        }
        for (InsightsViewModel.MeasureLine measure : viewModel.measureLines()) {
            Label name = new Label(measure.name() + (measure.unit() == null ? "" : " (" + measure.unit() + ")"));
            name.getStyleClass().add("insights-measure-title");
            Label caption = new Label(measure.caption());
            caption.getStyleClass().add("text-caption");
            goalBox.getChildren().addAll(name, caption,
                    line(measure.points(), measure.target(), null, measure.max(), measure.unit(), null));
        }
    }

    /** A line of points over time with an optional dashed target line. */
    private static Node line(List<InsightsViewModel.LinePoint> points, Double target, Double lower, Double upper,
                             String unit, String axisLabel) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setMinorTickVisible(false);
        y.setForceZeroInRange(lower != null);
        if (axisLabel != null) {
            y.setLabel(axisLabel);
        } else if (unit != null) {
            y.setLabel(unit);
        }
        if (lower != null && upper != null) {
            y.setAutoRanging(false);
            y.setLowerBound(lower);
            y.setUpperBound(upper);
            y.setTickUnit(25);
        }
        LineChart<String, Number> chart = new LineChart<>(x, y);
        chart.getStyleClass().add("insights-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setCreateSymbols(true);
        chart.setMinHeight(190);
        chart.setPrefHeight(200);

        XYChart.Series<String, Number> values = new XYChart.Series<>();
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            InsightsViewModel.LinePoint point = points.get(index);
            String label = point.label() + " ".repeat((int) labels.stream().filter(point.label()::equals).count());
            labels.add(point.label());
            XYChart.Data<String, Number> data = new XYChart.Data<>(label, point.value());
            data.nodeProperty().subscribe(node -> {
                if (node != null) {
                    Tooltip.install(node, new Tooltip(point.label() + " · " + point.value()));
                }
            });
            values.getData().add(data);
        }
        chart.getData().add(values);
        if (target != null && !values.getData().isEmpty()) {
            XYChart.Series<String, Number> goal = new XYChart.Series<>();
            goal.getData().add(new XYChart.Data<>(values.getData().getFirst().getXValue(), target));
            goal.getData().add(new XYChart.Data<>(values.getData().getLast().getXValue(), target));
            chart.getData().add(goal);
            goal.nodeProperty().subscribe(node -> {
                if (node != null) {
                    addClassLater(node, "target-line");
                }
            });
            chart.setCreateSymbols(true);
            goal.getData().forEach(data -> data.nodeProperty().subscribe(node -> {
                if (node != null) {
                    addClassLater(node, "target-symbol");
                }
            }));
        }
        return chart;
    }

    /** The chart resets a node's style classes right after creating it, so a class of ours goes on afterwards. */
    private static void addClassLater(Node node, String styleClass) {
        Platform.runLater(() -> {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        });
    }

    private static Node sectionHeader(String title, Node trailing) {
        Label label = new Label(title);
        label.getStyleClass().add("section-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = trailing == null ? new HBox(label) : new HBox(label, spacer, trailing);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }
}
