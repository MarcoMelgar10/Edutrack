package com.edutrack.desktop.system;

import com.edutrack.application.goal.GoalService;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * What the app can remind about: steps planned for today (or overdue), flashcards due, and practice
 * routines not yet logged once the reminder hour has passed.
 */
@Component
public class SpringReminderSource implements ReminderSource {

    private final GoalService goalService;
    private final ReviewService reviewService;
    private final PracticeService practiceService;
    private final UiPreferences preferences;
    private final Messages messages;

    public SpringReminderSource(GoalService goalService, ReviewService reviewService,
                                PracticeService practiceService, UiPreferences preferences, Messages messages) {
        this.goalService = goalService;
        this.reviewService = reviewService;
        this.practiceService = practiceService;
        this.preferences = preferences;
        this.messages = messages;
    }

    @Override
    public List<Reminder> due(LocalDate today, LocalTime now) {
        List<Reminder> reminders = new ArrayList<>();
        goalService.agenda(preferences.getWeekStart()).stream()
                .filter(item -> !item.plannedFor().isAfter(today))
                .forEach(item -> reminders.add(new Reminder("step:" + item.stepId(),
                        messages.get(item.overdue() ? "reminder.step.overdue" : "reminder.step.today"),
                        item.stepTitle() + " · " + item.goalTitle())));
        int cards = reviewService.dueCount(ReviewScope.ALL, preferences.getDailyNewCards(),
                preferences.getDailyReviewLimit());
        if (cards > 0) {
            reminders.add(new Reminder("cards", messages.get("reminder.cards.title"),
                    messages.plural("today.review.text", cards)));
        }
        if (now.getHour() >= preferences.getReminderHour()) {
            practiceService.today(preferences.getWeekStart()).stream()
                    .filter(status -> !status.doneToday())
                    .forEach(status -> reminders.add(new Reminder("routine:" + status.routine().id(),
                            messages.get("reminder.routine.title"),
                            status.routine().title() + " · " + status.goalTitle())));
        }
        return reminders;
    }
}
