package com.edutrack.desktop.ui.insights;

import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.session.SessionFormats;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;

/**
 * A calendar of study days: one column per week, one row per weekday, each day a small square that is
 * filled more the more was studied. Days after today are left empty. The darkness is the accent at four
 * strengths over the border color, so it reads the same in both themes.
 */
final class Heatmap extends VBox {

    static final int CELL = 13;
    static final int GAP = 3;

    Heatmap(InsightsViewModel.Heat heat, DayOfWeek weekStart, LocalDate today) {
        super(6);
        getStyleClass().add("heatmap");
        GridPane grid = new GridPane();
        grid.setHgap(GAP);
        grid.setVgap(GAP);

        // Month names above the first week of each month.
        int lastMonth = -1;
        for (int week = 0; week < heat.weeks(); week++) {
            LocalDate first = heat.firstDay().plusWeeks(week);
            if (first.getMonthValue() != lastMonth && first.getDayOfMonth() <= 7) {
                Label month = new Label(first.getMonth().getDisplayName(TextStyle.SHORT, I18n.locale()));
                month.getStyleClass().add("heat-label");
                grid.add(month, week + 1, 0, 3, 1);
                lastMonth = first.getMonthValue();
            }
        }
        // Weekday names on the left, every other row.
        for (int row = 0; row < 7; row++) {
            if (row % 2 == 0) {
                Label day = new Label(weekStart.plus(row).getDisplayName(TextStyle.SHORT, I18n.locale()));
                day.getStyleClass().add("heat-label");
                grid.add(day, 0, row + 1);
            }
        }
        for (int week = 0; week < heat.weeks(); week++) {
            for (int row = 0; row < 7; row++) {
                LocalDate date = heat.firstDay().plusWeeks(week).plusDays(row);
                if (date.isAfter(today)) {
                    continue;
                }
                int minutes = heat.minutes().getOrDefault(date, 0);
                Region cell = new Region();
                cell.setMinSize(CELL, CELL);
                cell.setPrefSize(CELL, CELL);
                cell.setMaxSize(CELL, CELL);
                cell.getStyleClass().addAll("heat-cell", "heat-" + level(minutes));
                Tooltip.install(cell, new Tooltip(SessionFormats.dayDate(date) + " · "
                        + (minutes == 0 ? I18n.get("insights.heat.none") : SessionFormats.duration(minutes))));
                grid.add(cell, week + 1, row + 1);
            }
        }

        HBox legend = new HBox(4);
        legend.setAlignment(Pos.CENTER_RIGHT);
        legend.setPadding(new Insets(4, 0, 0, 0));
        legend.getChildren().add(label(I18n.get("insights.heat.less")));
        for (int level = 0; level <= 4; level++) {
            Region sample = new Region();
            sample.setMinSize(CELL, CELL);
            sample.setPrefSize(CELL, CELL);
            sample.setMaxSize(CELL, CELL);
            sample.getStyleClass().addAll("heat-cell", "heat-" + level);
            legend.getChildren().add(sample);
        }
        legend.getChildren().add(label(I18n.get("insights.heat.more")));
        getChildren().addAll(grid, legend);
    }

    /** 0 nothing, 1 under half an hour, 2 under an hour, 3 under two hours, 4 two hours or more. */
    static int level(int minutes) {
        if (minutes <= 0) {
            return 0;
        }
        if (minutes < 30) {
            return 1;
        }
        if (minutes < 60) {
            return 2;
        }
        return minutes < 120 ? 3 : 4;
    }

    private static Label label(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("heat-label");
        return label;
    }
}
