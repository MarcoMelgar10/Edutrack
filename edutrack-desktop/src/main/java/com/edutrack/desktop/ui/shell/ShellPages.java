package com.edutrack.desktop.ui.shell;

import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.insights.InsightsController;
import com.edutrack.desktop.ui.journal.JournalController;
import com.edutrack.desktop.ui.review.ReviewController;
import com.edutrack.desktop.ui.search.SearchController;
import com.edutrack.desktop.ui.settings.SettingsController;
import com.edutrack.desktop.ui.today.TodayController;
import org.springframework.stereotype.Component;

/**
 * The single place that decides which controller (and so which FXML view) shows each {@link ShellPage}.
 * A feature that replaces a placeholder page changes exactly one line of {@link #controllerFor(ShellPage)}.
 */
@Component
public class ShellPages {

    private final ViewLoader viewLoader;

    public ShellPages(ViewLoader viewLoader) {
        this.viewLoader = viewLoader;
    }

    /** The controller class whose {@code XxxView.fxml} (next to it) is the page. */
    public Class<?> controllerFor(ShellPage page) {
        return switch (page) {
            case TODAY -> TodayController.class;
            case INSIGHTS -> InsightsController.class;
            case SETTINGS -> SettingsController.class;
            case JOURNAL -> JournalController.class;
            case REVIEW -> ReviewController.class;
            case SEARCH -> SearchController.class;
        };
    }

    /** Loads a fresh instance of the page. */
    public ViewLoader.LoadedView<?> load(ShellPage page) {
        ViewLoader.LoadedView<?> view = viewLoader.load(controllerFor(page));
        if (view.controller() instanceof PlaceholderController placeholder) {
            placeholder.show(page);
        }
        return view;
    }
}
