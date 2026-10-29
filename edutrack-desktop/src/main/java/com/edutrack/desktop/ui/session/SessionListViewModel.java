package com.edutrack.desktop.ui.session;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.session.StudySessionId;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The sessions of one step (the Sessions tab of the step page) or of a whole goal (the "all sessions" dialog),
 * newest first, with their total. It follows data changes while something is shown, so a session saved by the
 * timer or logged from Today appears at once.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SessionListViewModel {

    private final SessionService sessionService;
    private final DataChanges dataChanges;
    private final Messages messages;

    private final ObservableList<SessionView> sessions = FXCollections.observableArrayList();
    private final ObservableList<SessionView> sessionsReadOnly = FXCollections.unmodifiableObservableList(sessions);
    private final ReadOnlyStringWrapper totalText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper tabText = new ReadOnlyStringWrapper("");

    private GoalId goalId;
    private StepId stepId;
    private Subscription dataSubscription = Subscription.EMPTY;

    public SessionListViewModel(SessionService sessionService, DataChanges dataChanges, Messages messages) {
        this.sessionService = sessionService;
        this.dataChanges = dataChanges;
        this.messages = messages;
        tabText.set(messages.get("step.tab.sessions"));
    }

    /** Shows the sessions logged on one step of a goal. */
    public void showStep(GoalId goal, StepId step) {
        follow(goal, step);
    }

    /** Shows every session of a goal, whatever its step. */
    public void showGoal(GoalId goal) {
        follow(goal, null);
    }

    /** Shows nothing and stops following data changes. */
    public void clear() {
        dataSubscription.unsubscribe();
        goalId = null;
        stepId = null;
        sessions.clear();
        totalText.set("");
        tabText.set(messages.get("step.tab.sessions"));
    }

    private void follow(GoalId goal, StepId step) {
        dataSubscription.unsubscribe();
        goalId = goal;
        stepId = step;
        refresh();
        dataSubscription = dataChanges.subscribe(this::refresh);
    }

    public void refresh() {
        if (goalId == null) {
            return;
        }
        List<SessionView> loaded;
        try {
            loaded = stepId == null ? sessionService.forGoal(goalId) : sessionService.forStep(stepId);
        } catch (NotFoundException deleted) {
            loaded = List.of(); // the goal went away; the page showing it is being replaced
        }
        sessions.setAll(loaded);
        int total = loaded.stream().mapToInt(SessionView::minutes).sum();
        totalText.set(loaded.isEmpty() ? messages.get("session.total.none")
                : messages.plural("session.total", loaded.size(), SessionFormats.duration(total)));
        tabText.set(loaded.isEmpty() ? messages.get("step.tab.sessions")
                : messages.get("step.tab.sessionsCount", loaded.size()));
    }

    public void delete(StudySessionId id) {
        sessionService.delete(id);
        dataChanges.markChanged();
    }

    public GoalId goalId() {
        return goalId;
    }

    public StepId stepId() {
        return stepId;
    }

    /** Newest first. */
    public ObservableList<SessionView> sessions() {
        return sessionsReadOnly; // one shared wrapper: it only weakly listens to the source list
    }

    /** "4.5 h in 3 sessions", or a hint while there are none. */
    public ReadOnlyStringProperty totalTextProperty() {
        return totalText.getReadOnlyProperty();
    }

    /** "Sessions" or "Sessions (3)": the text of the tab. */
    public ReadOnlyStringProperty tabTextProperty() {
        return tabText.getReadOnlyProperty();
    }
}
