package com.edutrack.desktop.ui.roadmap;

import com.edutrack.domain.goal.StepStatus;
import javafx.scene.Group;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * The status mark on the timeline, drawn with shapes so it stays crisp at any scale:
 * ○ outlined circle (Not started), ◐ half-filled (In progress), ✔ filled with a check (Done).
 * Colors come from CSS ({@code .step-mark.not-started|in-progress|done}).
 */
public final class StepMark extends StackPane {

    private static final double RADIUS = 8;
    private static final String NOT_STARTED = "not-started";
    private static final String IN_PROGRESS = "in-progress";
    private static final String DONE = "done";

    public StepMark() {
        getStyleClass().add("step-mark");
        setMinSize(2 * RADIUS + 4, 2 * RADIUS + 4);
        setMaxSize(2 * RADIUS + 4, 2 * RADIUS + 4);
        setMouseTransparent(true); // the surrounding button handles clicks
    }

    public void show(StepStatus status) {
        getStyleClass().removeAll(NOT_STARTED, IN_PROGRESS, DONE);
        Circle ring = new Circle(0, 0, RADIUS);
        ring.getStyleClass().add("ring");

        switch (status) {
            case NOT_STARTED -> {
                getStyleClass().add(NOT_STARTED);
                getChildren().setAll(ring);
            }
            case IN_PROGRESS -> {
                getStyleClass().add(IN_PROGRESS);
                // left half filled; the full ring in the same Group keeps the mark centred
                Arc half = new Arc(0, 0, RADIUS - 2.5, RADIUS - 2.5, 90, 180);
                half.setType(ArcType.ROUND);
                half.getStyleClass().add("fill");
                getChildren().setAll(new Group(ring, half));
            }
            case DONE -> {
                getStyleClass().add(DONE);
                FontIcon check = new FontIcon(Feather.CHECK);
                check.getStyleClass().add("check");
                getChildren().setAll(ring, check);
            }
        }
    }
}
