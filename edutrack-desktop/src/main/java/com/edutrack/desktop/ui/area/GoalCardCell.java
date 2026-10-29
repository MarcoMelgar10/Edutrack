package com.edutrack.desktop.ui.area;

import com.edutrack.application.goal.GoalSummary;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Reorderable;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.Supplier;

/**
 * A goal card in the area page's list: title and percentage, a thin progress bar and a caption
 * with the deadline month and the number of finished steps. Completed goals read quieter; goals
 * past their deadline say so in the caption ("Overdue · 27 days"), in weight and icon, not color.
 * Cards can be reordered with the context menu, Alt+Up / Alt+Down and drag and drop.
 */
final class GoalCardCell extends ListCell<GoalSummary> {

    private static final String COMPLETED = "completed";
    private static final String OVERDUE = "overdue";

    private final Label title = new Label();
    private final Label percent = new Label();
    private final ProgressBar progress = new ProgressBar();
    private final Label caption = new Label();
    private final FontIcon overdueIcon = new FontIcon(Feather.ALERT_CIRCLE);
    private final VBox card;
    private final Supplier<LocalDate> today;
    private final MenuItem moveUp = new MenuItem(I18n.get("action.moveUp"));
    private final MenuItem moveDown = new MenuItem(I18n.get("action.moveDown"));
    private final ContextMenu menu = new ContextMenu(moveUp, moveDown);

    GoalCardCell(Supplier<LocalDate> today, Reorderable.Mover<GoalSummary> mover) {
        this.today = today;
        getStyleClass().add("goal-card-cell");

        title.getStyleClass().add("goal-card-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setWrapText(false);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        percent.getStyleClass().add("goal-card-percent");
        percent.setMinWidth(Region.USE_PREF_SIZE);
        HBox top = new HBox(title, percent);

        progress.getStyleClass().add("goal-card-progress");
        progress.setMaxWidth(Double.MAX_VALUE);
        caption.getStyleClass().addAll("text-caption", "goal-card-caption");
        overdueIcon.getStyleClass().add("goal-card-overdue-icon");

        card = new VBox(6, top, progress, caption);
        card.getStyleClass().add("goal-card");

        // A graphic is laid out at its preferred size. Pin the card to the cell's width so long titles
        // are cut with an ellipsis instead of widening the list, and the bar spans the whole card.
        setPrefWidth(0);
        card.prefWidthProperty().bind(widthProperty());

        moveUp.setOnAction(click -> mover.step(getItem(), -1));
        moveDown.setOnAction(click -> mover.step(getItem(), 1));
        Reorderable.install(this, mover);
    }

    @Override
    protected void updateItem(GoalSummary goal, boolean empty) {
        super.updateItem(goal, empty);
        getStyleClass().removeAll(COMPLETED, OVERDUE);
        caption.setGraphic(null);
        setText(null);
        if (empty || goal == null) {
            setGraphic(null);
            setContextMenu(null);
            return;
        }

        title.setText(goal.title());
        percent.setText(Formats.percent(goal.progress()));
        progress.setProgress(goal.progress().fraction());
        if (goal.completed()) {
            getStyleClass().add(COMPLETED);
            caption.setText(I18n.get("goal.card.completed", Formats.month(goal.deadline())));
        } else {
            String when = Formats.month(goal.deadline());
            if (goal.deadline() != null && goal.deadline().isBefore(today.get())) {
                long days = ChronoUnit.DAYS.between(goal.deadline(), today.get());
                when = I18n.plural("goal.card.overdue", days);
                getStyleClass().add(OVERDUE);
                caption.setGraphic(overdueIcon);
            }
            caption.setText(goal.stepCount() == 0 ? I18n.get("goal.card.noSteps", when)
                    : I18n.plural("goal.card.steps", goal.stepCount(), when, goal.doneCount()));
        }
        moveUp.setDisable(getIndex() == 0);
        moveDown.setDisable(getIndex() == getListView().getItems().size() - 1);
        setContextMenu(menu);
        setGraphic(card);
    }
}
