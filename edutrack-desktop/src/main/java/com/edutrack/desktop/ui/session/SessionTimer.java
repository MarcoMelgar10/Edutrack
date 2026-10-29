package com.edutrack.desktop.ui.session;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.session.SessionCommands;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionSource;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.util.Subscription;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * The study timer: the single source of truth for the session being timed, shared by the bar at the
 * bottom of the window, the step page and Today.
 *
 * <p>All timing is arithmetic on instants from the injected {@link Clock}; nothing here waits. A
 * separate ticker only calls {@link #tick()} once a second so the texts and the pomodoro phases move.
 * Only <b>focus</b> time counts: pauses and pomodoro breaks never add to the session that is saved.
 *
 * <p>Every state change (not every tick) is written to {@link UiPreferences}, so closing the app while
 * a session is active loses nothing: at the next start {@link #pendingRecovery()} offers to save,
 * resume or discard it.
 */
@Component
public class SessionTimer {

    public enum State { IDLE, RUNNING, PAUSED }

    public enum Mode { FREE, POMODORO }

    public enum Phase { FOCUS, BREAK }

    public enum NoticeKind {
        /** A focus block ended; the break is already running. */
        BREAK_STARTED,
        /** The break ended; the next focus block waits for the learner to resume. */
        FOCUS_READY
    }

    /** What is being studied. {@code title} is the text the bar shows ("IELTS 7.0 · Grammar review"). */
    public record Target(GoalId goalId, StepId stepId, PracticeRoutineId routineId, String title) {
    }

    /** A session found saved at start-up: what it was and how much focus time it holds. */
    public record Recovery(Target target, Instant startedAt, int minutes) {
    }

    public record Notice(NoticeKind kind, int completedBlocks) {
    }

    /** Shortest session that is saved: anything under a minute is offered for discarding. */
    static final long MIN_SAVED_MILLIS = 60_000;

    private static final int MAX_MINUTES = 1440;

    /** The numbers behind a session being timed; only changed through the timer's methods. */
    private static final class Run {
        final Target target;
        final Mode mode;
        final Instant startedAt;
        Phase phase = Phase.FOCUS;
        long focusMillis;
        long phaseMillis;
        Instant runningSince;
        int completedBlocks;

        Run(Target target, Mode mode, Instant startedAt) {
            this.target = target;
            this.mode = mode;
            this.startedAt = startedAt;
        }
    }

    private final SessionService sessionService;
    private final UiPreferences preferences;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.IDLE);
    private final ReadOnlyObjectWrapper<Target> target = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyObjectWrapper<Mode> mode = new ReadOnlyObjectWrapper<>(Mode.FREE);
    private final ReadOnlyStringWrapper elapsedText = new ReadOnlyStringWrapper("0:00");
    private final ReadOnlyStringWrapper phaseText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper remainingText = new ReadOnlyStringWrapper("");
    private final List<Consumer<Notice>> noticeListeners = new CopyOnWriteArrayList<>();

    private Run run;

    public SessionTimer(SessionService sessionService, UiPreferences preferences, DataChanges dataChanges,
                        Clock clock, Messages messages) {
        this.sessionService = sessionService;
        this.preferences = preferences;
        this.dataChanges = dataChanges;
        this.clock = clock;
        this.messages = messages;
    }

    // ---- control ----

    /** Starts timing a new session; there must be none active (the caller asks what to do with it first). */
    public void start(Target what, Mode how) {
        if (run != null) {
            throw new IllegalStateException("A session is already active");
        }
        Run started = new Run(what, how == null ? Mode.FREE : how, clock.instant());
        started.runningSince = started.startedAt;
        run = started;
        changed();
    }

    public void pause() {
        requireActive();
        Instant now = clock.instant();
        settle(now);
        if (run.runningSince != null) {
            closeSegment(now);
        }
        changed();
    }

    public void resume() {
        requireActive();
        Instant now = clock.instant();
        settle(now);
        if (run.runningSince == null) {
            run.runningSince = now;
        }
        changed();
    }

    /**
     * Ends the session and saves its focus time as a TIMER session started when the timer first
     * started. Under one minute nothing is saved: the result is empty and the timer stays paused, so
     * the caller can ask whether to {@link #discard()} it or carry on.
     *
     * @throws NotFoundException when the goal or step was deleted meanwhile; the timer is cleared
     */
    public Optional<SessionView> stop() {
        requireActive();
        Instant now = clock.instant();
        settle(now);
        if (run.runningSince != null) {
            closeSegment(now);
        }
        if (run.focusMillis < MIN_SAVED_MILLIS) {
            changed();
            return Optional.empty();
        }
        SessionView saved;
        try {
            saved = sessionService.log(new SessionCommands.LogSession(run.target.goalId(), run.target.stepId(),
                    run.target.routineId(), run.startedAt, minutesOf(run.focusMillis), null, SessionSource.TIMER));
        } catch (NotFoundException gone) {
            clear();
            throw gone;
        }
        clear();
        dataChanges.markChanged();
        return Optional.of(saved);
    }

    /** Forgets the session without saving anything. */
    public void discard() {
        if (run != null) {
            clear();
        }
    }

    /** Advances the pomodoro phases and refreshes the texts; called once a second by the ticker. */
    public void tick() {
        if (run == null) {
            return;
        }
        boolean phaseMoved = settle(clock.instant());
        if (phaseMoved) {
            changed();
        } else {
            refreshTexts(clock.instant());
        }
    }

    /** Rebuilds the texts, e.g. for a window built after a language switch. */
    public void refresh() {
        refreshTexts(clock.instant());
    }

    // ---- recovery ----

    /**
     * A session that was active when the app last ran, if any (and none is active now): the learner
     * decides whether to save it, {@linkplain #resumeRecovered() resume} it or
     * {@linkplain #discardRecovered() discard} it. A leftover with less than a minute of focus is
     * dropped on the spot.
     */
    public Optional<Recovery> pendingRecovery() {
        if (run != null) {
            return Optional.empty();
        }
        Optional<Run> saved = loadSaved();
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Run leftover = saved.get();
        advance(leftover, now);
        long focus = focusMillisAt(leftover, now);
        if (focus < MIN_SAVED_MILLIS) {
            preferences.clearSavedTimer();
            return Optional.empty();
        }
        return Optional.of(new Recovery(leftover.target, leftover.startedAt, minutesOf(focus)));
    }

    /** Carries on timing the saved session, counting the time the app was closed as it would have run. */
    public void resumeRecovered() {
        if (run != null) {
            return;
        }
        loadSaved().ifPresent(saved -> {
            run = saved;
            settle(clock.instant());
            changed();
        });
    }

    public void discardRecovered() {
        if (run == null) {
            preferences.clearSavedTimer();
        }
    }

    // ---- reading ----

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public boolean isActive() {
        return state.get() != State.IDLE;
    }

    public ReadOnlyObjectProperty<Target> targetProperty() {
        return target.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Mode> modeProperty() {
        return mode.getReadOnlyProperty();
    }

    /** The focus time so far as a clock: "42:10". */
    public ReadOnlyStringProperty elapsedTextProperty() {
        return elapsedText.getReadOnlyProperty();
    }

    /** "Focus 2", "Break", with "· Paused" while paused; empty for a running free session. */
    public ReadOnlyStringProperty phaseTextProperty() {
        return phaseText.getReadOnlyProperty();
    }

    /** "12:30 left" in a pomodoro phase; empty in a free session. */
    public ReadOnlyStringProperty remainingTextProperty() {
        return remainingText.getReadOnlyProperty();
    }

    /** Focus time so far, in whole seconds. */
    public long focusSeconds() {
        return run == null ? 0 : focusMillisAt(run, clock.instant()) / 1000;
    }

    /** The phase of a pomodoro session ({@link Phase#FOCUS} for a free one or when idle). */
    public Phase phase() {
        return run == null ? Phase.FOCUS : run.phase;
    }

    /** Focus blocks finished so far in this pomodoro session. */
    public int completedBlocks() {
        return run == null ? 0 : run.completedBlocks;
    }

    /** True when {@link #stop()} would find less than a minute of focus. */
    public boolean isTooShort() {
        return run != null && focusMillisAt(run, clock.instant()) < MIN_SAVED_MILLIS;
    }

    /** Hears about pomodoro phase changes; unsubscribe when the listener goes away. */
    public Subscription onNotice(Consumer<Notice> listener) {
        noticeListeners.add(listener);
        return () -> noticeListeners.remove(listener);
    }

    // ---- the arithmetic ----

    /** Moves the pomodoro to {@code now}; true when a phase changed. Notices go to the listeners. */
    private boolean settle(Instant now) {
        List<Notice> notices = advance(run, now);
        notices.forEach(notice -> noticeListeners.forEach(listener -> listener.accept(notice)));
        return !notices.isEmpty();
    }

    /**
     * Plays the phase changes of a pomodoro that happened up to {@code now}. A focus block ends in a
     * running break; a break ends in a paused focus block that waits for the learner. If the app slept
     * through several phases they are all applied, each at its own exact instant.
     */
    private List<Notice> advance(Run timed, Instant now) {
        List<Notice> notices = new ArrayList<>();
        if (timed.mode != Mode.POMODORO) {
            return notices;
        }
        while (timed.runningSince != null) {
            long length = phaseLengthMillis(timed);
            long left = length - timed.phaseMillis - millisBetween(timed.runningSince, now);
            if (left > 0) {
                break;
            }
            Instant boundary = timed.runningSince.plusMillis(Math.max(0, length - timed.phaseMillis));
            if (timed.phase == Phase.FOCUS) {
                timed.focusMillis += millisBetween(timed.runningSince, boundary);
                timed.completedBlocks++;
                timed.phase = Phase.BREAK;
                timed.phaseMillis = 0;
                timed.runningSince = boundary;
                notices.add(new Notice(NoticeKind.BREAK_STARTED, timed.completedBlocks));
            } else {
                timed.phase = Phase.FOCUS;
                timed.phaseMillis = 0;
                timed.runningSince = null;
                notices.add(new Notice(NoticeKind.FOCUS_READY, timed.completedBlocks));
            }
        }
        return notices;
    }

    private void closeSegment(Instant now) {
        long segment = millisBetween(run.runningSince, now);
        if (run.phase == Phase.FOCUS) {
            run.focusMillis += segment;
        }
        run.phaseMillis += segment;
        run.runningSince = null;
    }

    private long focusMillisAt(Run timed, Instant now) {
        boolean focusing = timed.runningSince != null && timed.phase == Phase.FOCUS;
        return timed.focusMillis + (focusing ? millisBetween(timed.runningSince, now) : 0);
    }

    private long phaseMillisAt(Run timed, Instant now) {
        return timed.phaseMillis + (timed.runningSince != null ? millisBetween(timed.runningSince, now) : 0);
    }

    private long phaseLengthMillis(Run timed) {
        int minutes = timed.phase == Phase.FOCUS
                ? preferences.getPomodoroFocusMinutes() : preferences.getPomodoroBreakMinutes();
        return minutes * 60_000L;
    }

    private static long millisBetween(Instant from, Instant to) {
        return Math.max(0, Duration.between(from, to).toMillis());
    }

    /** Whole minutes, half up, at most a day. */
    private static int minutesOf(long millis) {
        return (int) Math.min(MAX_MINUTES, Math.max(1, (millis + 30_000) / 60_000));
    }

    private void requireActive() {
        if (run == null) {
            throw new IllegalStateException("No session is active");
        }
    }

    // ---- state, texts, persistence ----

    /** After every state change: publish the new state and texts, and keep the saved copy current. */
    private void changed() {
        Instant now = clock.instant();
        refreshTexts(now);
        persist();
    }

    private void clear() {
        run = null;
        preferences.clearSavedTimer();
        refreshTexts(clock.instant());
    }

    private void refreshTexts(Instant now) {
        if (run == null) {
            state.set(State.IDLE);
            target.set(null);
            mode.set(Mode.FREE);
            elapsedText.set(SessionFormats.clock(0));
            phaseText.set("");
            remainingText.set("");
            return;
        }
        boolean paused = run.runningSince == null;
        state.set(paused ? State.PAUSED : State.RUNNING);
        target.set(run.target);
        mode.set(run.mode);
        elapsedText.set(SessionFormats.clock(focusMillisAt(run, now) / 1000));
        if (run.mode == Mode.POMODORO) {
            String phaseName = run.phase == Phase.FOCUS
                    ? messages.get("session.phase.focus", run.completedBlocks + 1)
                    : messages.get("session.phase.break");
            phaseText.set(paused ? messages.get("session.phase.paused", phaseName) : phaseName);
            long leftMillis = Math.max(0, phaseLengthMillis(run) - phaseMillisAt(run, now));
            remainingText.set(messages.get("session.remaining", SessionFormats.clock((leftMillis + 999) / 1000)));
        } else {
            phaseText.set(paused ? messages.get("session.paused") : "");
            remainingText.set("");
        }
    }

    private void persist() {
        preferences.saveTimer(new UiPreferences.TimerSnapshot(run.target.goalId().toString(),
                run.target.stepId() == null ? null : run.target.stepId().toString(),
                run.target.routineId() == null ? null : run.target.routineId().toString(),
                run.target.title(), run.mode.name(), run.phase.name(), run.startedAt, run.focusMillis,
                run.phaseMillis, run.runningSince, run.completedBlocks));
    }

    /** The saved timer as a run; a copy that cannot be read is removed. */
    private Optional<Run> loadSaved() {
        Optional<UiPreferences.TimerSnapshot> saved = preferences.getSavedTimer();
        if (saved.isEmpty()) {
            return Optional.empty();
        }
        UiPreferences.TimerSnapshot snapshot = saved.get();
        try {
            Target what = new Target(new GoalId(UUID.fromString(snapshot.goalId())),
                    snapshot.stepId() == null ? null : new StepId(UUID.fromString(snapshot.stepId())),
                    snapshot.routineId() == null ? null : new PracticeRoutineId(UUID.fromString(snapshot.routineId())),
                    snapshot.title());
            Run restored = new Run(what, Mode.valueOf(snapshot.mode()), snapshot.startedAt());
            restored.phase = Phase.valueOf(snapshot.phase());
            restored.focusMillis = Math.max(0, snapshot.focusMillis());
            restored.phaseMillis = Math.max(0, snapshot.phaseMillis());
            restored.runningSince = snapshot.runningSince();
            restored.completedBlocks = Math.max(0, snapshot.completedBlocks());
            return Optional.of(restored);
        } catch (IllegalArgumentException unreadable) {
            preferences.clearSavedTimer();
            return Optional.empty();
        }
    }
}
