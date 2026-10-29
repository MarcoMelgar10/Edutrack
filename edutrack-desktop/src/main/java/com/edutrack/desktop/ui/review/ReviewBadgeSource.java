package com.edutrack.desktop.ui.review;

import com.edutrack.application.review.ReviewScope;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.desktop.ui.shell.NavBadgeSource;
import com.edutrack.desktop.ui.shell.ShellPage;
import org.springframework.stereotype.Component;

/** The number on the Review entry of the rail: cards due plus new cards, within today's daily limits. */
@Component
public class ReviewBadgeSource implements NavBadgeSource {

    private final ReviewService reviews;
    private final UiPreferences preferences;

    public ReviewBadgeSource(ReviewService reviews, UiPreferences preferences) {
        this.reviews = reviews;
        this.preferences = preferences;
    }

    @Override
    public ShellPage page() {
        return ShellPage.REVIEW;
    }

    @Override
    public int count() {
        return reviews.dueCount(ReviewScope.ALL, preferences.getDailyNewCards(), preferences.getDailyReviewLimit());
    }
}
