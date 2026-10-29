package com.edutrack.desktop.ui.session;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.session.SessionSource;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Optional;

/**
 * Everything the learner can do with the study timer from a window: start a session on a goal, a step or
 * a routine, stop and save it, discard it, and decide what to do with one found saved at start-up. It
 * wraps {@link SessionTimer} with the questions and dialogs those actions need, so every place with a
 * "Start session" button behaves the same.
 */
@Component
public class SessionLauncher {

    private final SessionTimer timer;
    private final SessionPrompts prompts;
    private final FormDialogs dialogs;
    private final GoalService goalService;
    private final Clock clock;

    public SessionLauncher(SessionTimer timer, SessionPrompts prompts, FormDialogs dialogs, GoalService goalService,
                           Clock clock) {
        this.timer = timer;
        this.prompts = prompts;
        this.dialogs = dialogs;
        this.goalService = goalService;
        this.clock = clock;
    }

    // ---- starting ----

    /** Starts timing the goal, or one of its steps when {@code stepId} is given. */
    public void start(Window owner, GoalId goalId, StepId stepId, SessionTimer.Mode mode) {
        GoalDetails goal = goalService.getGoal(goalId);
        String title = goal.title();
        if (stepId != null) {
            title += " · " + goal.step(stepId).title();
        }
        begin(owner, new SessionTimer.Target(goalId, stepId, null, title), mode);
    }

    /** Starts timing a practice routine; the saved session counts towards it. */
    public void startRoutine(Window owner, RoutineView routine, String goalTitle) {
        begin(owner, new SessionTimer.Target(routine.goalId(), routine.stepId(), routine.id(),
                routine.title() + " · " + goalTitle), SessionTimer.Mode.FREE);
    }

    /** Asks what to study (a goal, optionally a step, free or pomodoro) and starts timing it. */
    public void pickAndStart(Window owner, GoalId goalId, StepId stepId) {
        dialogs.show(owner, StartSessionDialogController.class, form -> form.preselect(goalId, stepId))
                .ifPresent(choice -> start(owner, choice.goalId(), choice.stepId(), choice.mode()));
    }

    private void begin(Window owner, SessionTimer.Target target, SessionTimer.Mode mode) {
        if (timer.isActive()) {
            String current = timer.targetProperty().get() == null ? "" : timer.targetProperty().get().title();
            if (!prompts.confirmStopCurrent(owner, current) || !stopAndSave(owner)) {
                return;
            }
        }
        timer.start(target, mode);
    }

    // ---- ending ----

    /**
     * Stops the session and saves it. Under a minute nothing is saved: the learner is asked whether to
     * discard it, and if not the timer stays paused.
     *
     * @return {@code true} when the timer is idle afterwards
     */
    public boolean stopAndSave(Window owner) {
        if (timer.stop().isPresent()) {
            return true;
        }
        if (prompts.confirmDiscardShort(owner)) {
            timer.discard();
            return true;
        }
        return !timer.isActive();
    }

    /** Throws the session away after asking, since the time counted so far is lost. */
    public void discard(Window owner) {
        if (prompts.confirmDiscard(owner, SessionFormats.clock(timer.focusSeconds()))) {
            timer.discard();
        }
    }

    // ---- start-up recovery ----

    /**
     * If the app was closed while a session was active, asks whether to save it (the Log session dialog opens
     * filled in, minutes editable), carry on timing it, or discard it.
     */
    public void offerRecovery(Window owner) {
        Optional<SessionTimer.Recovery> found = timer.pendingRecovery();
        while (found.isPresent()) {
            SessionTimer.Recovery recovery = found.get();
            try {
                goalService.getGoal(recovery.target().goalId());
            } catch (NotFoundException gone) {
                timer.discardRecovered(); // its goal was deleted: there is nothing to save the time on
                return;
            }
            String since = Formats.time(recovery.startedAt(), clock.getZone());
            SessionPrompts.RecoveryChoice choice = prompts.recovery(owner, recovery.target().title(), since,
                    recovery.minutes());
            switch (choice) {
                case RESUME -> {
                    timer.resumeRecovered();
                    return;
                }
                case DISCARD -> {
                    timer.discardRecovered();
                    return;
                }
                case SAVE -> {
                    SessionTimer.Target target = recovery.target();
                    boolean saved = dialogs.show(owner, LogSessionDialogController.class,
                            form -> form.prefill(target.goalId(), target.stepId(), target.routineId(),
                                    recovery.startedAt(), recovery.minutes(), SessionSource.TIMER)).isPresent();
                    if (saved) {
                        timer.discardRecovered();
                        return;
                    }
                    // Cancelled: the session is still saved, so ask again rather than lose it silently.
                }
            }
        }
    }
}
