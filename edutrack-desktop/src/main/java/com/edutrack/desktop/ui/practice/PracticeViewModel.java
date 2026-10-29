package com.edutrack.desktop.ui.practice;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.practice.RoutineStatus;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.application.progress.GoalPace;
import com.edutrack.application.progress.PaceService;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.session.SessionTotals;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.SessionFormats;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.progress.Pace;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * State of the Practice tab of a goal: the time logged against its estimate, how its pace compares with
 * what the plan needs, and its practice routines with this week's progress. Follows the data while a goal
 * is shown, so a session logged anywhere updates it.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class PracticeViewModel {

    private final SessionService sessionService;
    private final PaceService paceService;
    private final PracticeService practiceService;
    private final UiPreferences preferences;
    private final DataChanges dataChanges;
    private final Messages messages;

    private final ReadOnlyStringWrapper timeText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper paceText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper paceBehind = new ReadOnlyBooleanWrapper();
    private final ReadOnlyBooleanWrapper paceVisible = new ReadOnlyBooleanWrapper();
    private final ReadOnlyStringWrapper routinesTitle = new ReadOnlyStringWrapper("");
    private final ObservableList<RoutineRow> routines = FXCollections.observableArrayList();
    private final ObservableList<RoutineRow> routinesReadOnly = FXCollections.unmodifiableObservableList(routines);

    private GoalDetails goal;
    private Subscription following = Subscription.EMPTY;

    public PracticeViewModel(SessionService sessionService, PaceService paceService, PracticeService practiceService,
                             UiPreferences preferences, DataChanges dataChanges, Messages messages) {
        this.sessionService = sessionService;
        this.paceService = paceService;
        this.practiceService = practiceService;
        this.preferences = preferences;
        this.dataChanges = dataChanges;
        this.messages = messages;
    }

    /** Shows (or refreshes) a goal and starts following the data. */
    public void show(GoalDetails shown) {
        this.goal = shown;
        following.unsubscribe();
        following = dataChanges.subscribe(this::refresh);
        refresh();
    }

    public void clear() {
        following.unsubscribe();
        following = Subscription.EMPTY;
        goal = null;
    }

    public void refresh() {
        if (goal == null) {
            return;
        }
        GoalId id = goal.id();
        timeText.set(timeText(sessionService.totals(id), goal));
        showPace(paceService.pace(id));
        showRoutines(id);
    }

    // ---- actions ----

    public void pause(PracticeRoutineId id) {
        practiceService.pause(id);
        dataChanges.markChanged();
    }

    public void resume(PracticeRoutineId id) {
        practiceService.resume(id);
        dataChanges.markChanged();
    }

    public void delete(PracticeRoutineId id) {
        practiceService.delete(id);
        dataChanges.markChanged();
    }

    public void move(PracticeRoutineId id, MoveDirection direction) {
        practiceService.move(id, direction);
        dataChanges.markChanged();
    }

    // ---- text ----

    /** "Logged 41 h · estimated 196 h · last 7 days 3.5 h" (the estimate and the week only when known). */
    String timeText(SessionTotals totals, GoalDetails shown) {
        StringBuilder text = new StringBuilder(messages.get("practice.time.logged",
                SessionFormats.duration(totals.goalMinutes())));
        BigDecimal estimated = shown.totalHours();
        if (estimated != null && estimated.signum() > 0) {
            text.append(" · ").append(messages.get("practice.time.estimated", Formats.number(estimated)));
        }
        if (totals.last7DaysMinutes() > 0) {
            text.append(" · ")
                    .append(messages.get("practice.time.week", SessionFormats.duration(totals.last7DaysMinutes())));
        }
        return text.toString();
    }

    private void showPace(GoalPace goalPace) {
        Pace pace = goalPace.pace();
        paceBehind.set(pace.status() == Pace.PaceStatus.BEHIND);
        paceVisible.set(pace.status() != Pace.PaceStatus.NO_PLAN || pace.actualMinutesPerWeek() > 0);
        paceText.set(paceText(pace));
    }

    /** "On track · needs 5 h/week · doing 5.5 h/week · finishes about 12 Mar" and its variants. */
    String paceText(Pace pace) {
        StringBuilder text = new StringBuilder(messages.get("practice.pace." + pace.status().name()));
        if (pace.status() == Pace.PaceStatus.FINISHED) {
            return text.toString();
        }
        if (pace.requiredMinutesPerWeek() != null) {
            text.append(" · ")
                    .append(messages.get("practice.pace.needs", SessionFormats.perWeek(pace.requiredMinutesPerWeek())));
        }
        text.append(" · ").append(messages.get("practice.pace.doing", SessionFormats.perWeek(pace.actualMinutesPerWeek())));
        if (pace.projectedFinish() != null && pace.status() != Pace.PaceStatus.NO_PLAN) {
            text.append(" · ")
                    .append(messages.get("practice.pace.finishes", SessionFormats.dayMonth(pace.projectedFinish())));
        }
        return text.toString();
    }

    private void showRoutines(GoalId id) {
        List<RoutineView> all = practiceService.forGoal(id);
        Map<PracticeRoutineId, RoutineStatus> status = practiceService.today(preferences.getWeekStart()).stream()
                .filter(each -> each.routine().goalId().equals(id))
                .collect(Collectors.toMap(each -> each.routine().id(), each -> each));
        routines.setAll(all.stream().map(routine -> row(routine, status.get(routine.id()))).toList());
        routinesTitle.set(all.isEmpty() ? messages.get("practice.routines.title")
                : messages.get("practice.routines.titleCount", all.size()));
    }

    RoutineRow row(RoutineView routine, RoutineStatus status) {
        String duration = SessionFormats.duration(routine.targetMinutes());
        String cadence = routine.cadence() == Cadence.DAILY
                ? messages.get("practice.cadence.daily", duration)
                : messages.get("practice.cadence.weekly", routine.timesPerWeek(), duration);
        String week;
        if (!routine.active()) {
            week = messages.get("practice.routine.paused");
        } else if (status == null) {
            week = "";
        } else if (routine.cadence() == Cadence.DAILY) {
            week = messages.get("practice.week.daily", status.sessionsThisWeek());
        } else {
            week = messages.get("practice.week.weekly", status.sessionsThisWeek(), routine.timesPerWeek());
        }
        return new RoutineRow(routine, cadence, week, status != null && status.doneToday(),
                status != null && status.metThisWeek());
    }

    // ---- reading ----

    public ReadOnlyStringProperty timeTextProperty() {
        return timeText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty paceTextProperty() {
        return paceText.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty paceBehindProperty() {
        return paceBehind.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty paceVisibleProperty() {
        return paceVisible.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty routinesTitleProperty() {
        return routinesTitle.getReadOnlyProperty();
    }

    public ObservableList<RoutineRow> routines() {
        return routinesReadOnly;
    }

    public GoalDetails goal() {
        return goal;
    }
}
