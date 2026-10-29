package com.edutrack.desktop.ui.today;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.AgendaItem;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.journal.JournalService;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.practice.RoutineStatus;
import com.edutrack.application.progress.ConsistencyService;
import com.edutrack.application.progress.GoalPace;
import com.edutrack.application.progress.PaceService;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.SessionFormats;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.progress.Pace;
import com.edutrack.domain.progress.StreakCalculator;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The Today page: what to do now. The long date and streak, the weekly check-in when it is due, the cards
 * waiting for review, today's practice routines, the steps planned for this week, the goals that fell
 * behind their pace, and the goals still in progress. Every section is empty (and hidden) when there is
 * nothing to show; with no areas at all the page shows the first-run welcome.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TodayViewModel {

    /** One goal in the list: what the row shows. */
    public record GoalRow(GoalId goalId, String title, String areaName, double progress, String progressText,
                          String deadlineText) {
    }

    /** An active practice routine: {@code minutes} is what one click on "Log" records. */
    public record RoutineItem(PracticeRoutineId id, GoalId goalId, StepId stepId, String title, String goalTitle,
                              String detailText, int minutes, boolean doneToday, boolean metThisWeek) {
    }

    /** A step planned for this week (or before: overdue). */
    public record AgendaRow(GoalId goalId, StepId stepId, String stepTitle, String goalTitle, String dateText,
                            boolean overdue) {
    }

    /** A goal behind its pace and what it needs. */
    public record AttentionRow(GoalId goalId, String title, String detailText) {
    }

    private final AreaService areaService;
    private final GoalService goalService;
    private final PracticeService practiceService;
    private final PaceService paceService;
    private final ConsistencyService consistencyService;
    private final JournalService journalService;
    private final ReviewService reviewService;
    private final UiPreferences preferences;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ReadOnlyBooleanWrapper noAreas = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyStringWrapper dateText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper streakText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper checkInDue = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper reviewText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper reviewDue = new ReadOnlyBooleanWrapper();
    private final ObservableList<RoutineItem> routines = FXCollections.observableArrayList();
    private final ObservableList<RoutineItem> routinesReadOnly = FXCollections.unmodifiableObservableList(routines);
    private final ObservableList<AgendaRow> agenda = FXCollections.observableArrayList();
    private final ObservableList<AgendaRow> agendaReadOnly = FXCollections.unmodifiableObservableList(agenda);
    private final ObservableList<AttentionRow> attention = FXCollections.observableArrayList();
    private final ObservableList<AttentionRow> attentionReadOnly = FXCollections.unmodifiableObservableList(attention);
    private final ObservableList<GoalRow> goals = FXCollections.observableArrayList();
    private final ObservableList<GoalRow> goalsReadOnly = FXCollections.unmodifiableObservableList(goals);
    private Subscription dataSubscription = Subscription.EMPTY;

    public TodayViewModel(AreaService areaService, GoalService goalService, PracticeService practiceService,
                          PaceService paceService, ConsistencyService consistencyService,
                          JournalService journalService, ReviewService reviewService, UiPreferences preferences,
                          DataChanges dataChanges, Clock clock, Messages messages) {
        this.areaService = areaService;
        this.goalService = goalService;
        this.practiceService = practiceService;
        this.paceService = paceService;
        this.consistencyService = consistencyService;
        this.journalService = journalService;
        this.reviewService = reviewService;
        this.preferences = preferences;
        this.dataChanges = dataChanges;
        this.clock = clock;
        this.messages = messages;
        refresh();
        dataSubscription = dataChanges.subscribe(this::refresh);
    }

    /** Stops following data changes; called when the shell holding the page is replaced. */
    public void dispose() {
        dataSubscription.unsubscribe();
    }

    public void refresh() {
        LocalDate today = LocalDate.now(clock);
        dateText.set(Formats.longDate(today));

        List<AreaSummary> areas = areaService.listAreas();
        noAreas.set(areas.isEmpty());

        List<GoalSummary> inProgress = new ArrayList<>();
        for (AreaSummary area : areas) {
            goalService.listGoals(area.id()).stream().filter(goal -> !goal.completed()).forEach(inProgress::add);
        }
        goals.setAll(inProgress.stream()
                .sorted(Comparator.comparing(GoalSummary::deadline, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(goal -> row(goal, areas, today))
                .toList());

        if (areas.isEmpty()) {
            streakText.set("");
            checkInDue.set(false);
            reviewDue.set(false);
            routines.clear();
            agenda.clear();
            attention.clear();
            return;
        }
        streakText.set(streakText(consistencyService.streak(), consistencyService.daysThisWeek(preferences.getWeekStart())));
        checkInDue.set(journalService.checkInDue(preferences.getCheckInDay()));
        showReview();
        showRoutines();
        showAgenda();
        showAttention();
    }

    // ---- sections ----

    /** "5-day streak · 3 study days this week · best 12", or an invitation to start one. */
    String streakText(StreakCalculator.Streak streak, int daysThisWeek) {
        if (streak.current() == 0 && streak.longest() == 0 && daysThisWeek == 0) {
            return messages.get("today.streak.none");
        }
        List<String> parts = new ArrayList<>();
        parts.add(streak.current() > 0 ? messages.plural("today.streak.current", streak.current())
                : messages.get("today.streak.broken"));
        parts.add(messages.plural("today.streak.week", daysThisWeek));
        if (streak.longest() > streak.current()) {
            parts.add(messages.get("today.streak.best", streak.longest()));
        }
        return String.join(" · ", parts);
    }

    private void showReview() {
        int due = reviewService.dueCount(ReviewScope.ALL, preferences.getDailyNewCards(),
                preferences.getDailyReviewLimit());
        reviewDue.set(due > 0);
        reviewText.set(due > 0 ? messages.plural("today.review.text", due) : "");
    }

    private void showRoutines() {
        routines.setAll(practiceService.today(preferences.getWeekStart()).stream().map(this::routineItem).toList());
    }

    RoutineItem routineItem(RoutineStatus status) {
        var routine = status.routine();
        String detail = routine.cadence() == com.edutrack.domain.practice.Cadence.DAILY
                ? messages.get("practice.week.daily", status.sessionsThisWeek())
                : messages.get("practice.week.weekly", status.sessionsThisWeek(), routine.timesPerWeek());
        return new RoutineItem(routine.id(), routine.goalId(), routine.stepId(), routine.title(), status.goalTitle(),
                detail, routine.targetMinutes(), status.doneToday(), status.metThisWeek());
    }

    private void showAgenda() {
        agenda.setAll(goalService.agenda(preferences.getWeekStart()).stream().map(this::agendaRow).toList());
    }

    AgendaRow agendaRow(AgendaItem item) {
        String date = item.overdue()
                ? messages.get("today.agenda.overdue", SessionFormats.dayMonth(item.plannedFor()))
                : SessionFormats.dayDate(item.plannedFor());
        return new AgendaRow(item.goalId(), item.stepId(), item.stepTitle(), item.goalTitle(), date, item.overdue());
    }

    private void showAttention() {
        attention.setAll(paceService.all().stream()
                .filter(goalPace -> goalPace.pace().status() == Pace.PaceStatus.BEHIND)
                .map(this::attentionRow)
                .toList());
    }

    AttentionRow attentionRow(GoalPace goalPace) {
        Pace pace = goalPace.pace();
        String needs = pace.requiredMinutesPerWeek() == null ? ""
                : messages.get("practice.pace.needs", SessionFormats.perWeek(pace.requiredMinutesPerWeek())) + " · ";
        return new AttentionRow(goalPace.goalId(), goalPace.title(),
                needs + messages.get("practice.pace.doing", SessionFormats.perWeek(pace.actualMinutesPerWeek())));
    }

    private static GoalRow row(GoalSummary goal, List<AreaSummary> areas, LocalDate today) {
        String areaName = areas.stream().filter(area -> area.id().equals(goal.areaId()))
                .map(AreaSummary::name).findFirst().orElse("");
        return new GoalRow(goal.id(), goal.title(), areaName, goal.progress().fraction(),
                Formats.percent(goal.progress()), Formats.deadline(goal.deadline(), today));
    }

    // ---- actions ----

    /** Records the routine's usual minutes as a session that ended now: the one-click "Log 15 min". */
    public void logRoutine(RoutineItem routine) {
        practiceService.logPractice(routine.id(), routine.minutes(), null);
        dataChanges.markChanged();
    }

    /** Creates English, Technologies and Consulting (skipping names that already exist). */
    public void addSuggestedAreas() {
        areaService.addSuggestedAreas();
        dataChanges.markChanged();
    }

    // ---- reading ----

    /** "Tuesday, 27 October 2026" */
    public ReadOnlyStringProperty dateTextProperty() {
        return dateText.getReadOnlyProperty();
    }

    /** True when there are no areas yet: the page shows the welcome. */
    public ReadOnlyBooleanProperty noAreasProperty() {
        return noAreas.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty streakTextProperty() {
        return streakText.getReadOnlyProperty();
    }

    /** True from the check-in day on until a check-in has been written. */
    public ReadOnlyBooleanProperty checkInDueProperty() {
        return checkInDue.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty reviewDueProperty() {
        return reviewDue.getReadOnlyProperty();
    }

    /** "18 cards to review". */
    public ReadOnlyStringProperty reviewTextProperty() {
        return reviewText.getReadOnlyProperty();
    }

    public ObservableList<RoutineItem> routines() {
        return routinesReadOnly;
    }

    public ObservableList<AgendaRow> agenda() {
        return agendaReadOnly;
    }

    public ObservableList<AttentionRow> attention() {
        return attentionReadOnly;
    }

    /** Goals not yet completed, nearest deadline first (goals without a deadline last). */
    public ObservableList<GoalRow> goals() {
        return goalsReadOnly; // one shared wrapper: it only weakly listens to the source list
    }
}
