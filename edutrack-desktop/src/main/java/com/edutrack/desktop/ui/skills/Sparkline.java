package com.edutrack.desktop.ui.skills;

import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Polyline;

import java.util.List;

/** A tiny trend line: heights from 0 (low) to 1 (high), oldest first, the last point marked. */
final class Sparkline extends Pane {

    static final double WIDTH = 96;
    static final double HEIGHT = 26;
    private static final double PADDING = 3;

    Sparkline(List<Double> heights) {
        getStyleClass().add("sparkline");
        setMinSize(WIDTH, HEIGHT);
        setPrefSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        if (heights.size() < 2) {
            return;
        }
        Polyline line = new Polyline();
        line.getStyleClass().add("sparkline-line");
        double step = (WIDTH - 2 * PADDING) / (heights.size() - 1);
        double lastX = 0;
        double lastY = 0;
        for (int index = 0; index < heights.size(); index++) {
            lastX = PADDING + index * step;
            lastY = HEIGHT - PADDING - heights.get(index) * (HEIGHT - 2 * PADDING);
            line.getPoints().addAll(lastX, lastY);
        }
        Circle end = new Circle(lastX, lastY, 2.5);
        end.getStyleClass().add("sparkline-end");
        getChildren().addAll(line, end);
    }
}
