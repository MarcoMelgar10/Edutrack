package com.edutrack.desktop.ui.session;

import com.edutrack.application.session.SessionCommands;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySessionId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionTimerTest {

    /** A clock the test moves by hand. */
    private static final class TestClock extends Clock {
        private Instant now = Instant.parse("2026-10-28T18:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final SessionService sessions = mock(SessionService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final GoalId goal = GoalId.newId();
    private final StepId step = StepId.newId();
    private final SessionTimer.Target target = new SessionTimer.Target(goal, step, null, "IELTS · Grammar");
    private Preferences node;
    private UiPreferences preferences;
    private SessionTimer timer;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("edutrack-session-timer-test-" + UUID.randomUUID());
        preferences = new UiPreferences(node);
        timer = newTimer();
        when(sessions.log(any())).thenAnswer(call -> {
            SessionCommands.LogSession command = call.getArgument(0);
            return new SessionView(StudySessionId.newId(), command.goalId(), command.stepId(), null,
                    command.routineId(), command.startedAt(), command.minutes(), command.note(), command.source());
        });
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    private SessionTimer newTimer() {
        return new SessionTimer(sessions, preferences, dataChanges, clock, new Messages());
    }

    private SessionCommands.LogSession savedSession() {
        ArgumentCaptor<SessionCommands.LogSession> captured = ArgumentCaptor.forClass(SessionCommands.LogSession.class);
        verify(sessions).log(captured.capture());
        return captured.getValue();
    }

    @Test
    void aFreeSessionSavesTheTimeFromWhenItStarted() {
        Instant startedAt = clock.instant();
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofMinutes(42));
        timer.tick();

        assertThat(timer.elapsedTextProperty().get()).isEqualTo("42:00");
        Optional<SessionView> saved = timer.stop();

        assertThat(saved).isPresent();
        SessionCommands.LogSession command = savedSession();
        assertThat(command.minutes()).isEqualTo(42);
        assertThat(command.startedAt()).isEqualTo(startedAt);
        assertThat(command.goalId()).isEqualTo(goal);
        assertThat(command.stepId()).isEqualTo(step);
        assertThat(command.source()).isEqualTo(SessionSource.TIMER);
        assertThat(timer.isActive()).isFalse();
        assertThat(preferences.getSavedTimer()).isEmpty();
    }

    @Test
    void pausedTimeIsNotCounted() {
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofMinutes(5));
        timer.pause();
        clock.advance(Duration.ofMinutes(20));
        assertThat(timer.stateProperty().get()).isEqualTo(SessionTimer.State.PAUSED);
        timer.resume();
        clock.advance(Duration.ofMinutes(5));

        timer.stop();

        assertThat(savedSession().minutes()).isEqualTo(10);
    }

    @Test
    void underAMinuteIsNotSavedAndTheTimerWaitsForTheLearner() {
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofSeconds(40));

        assertThat(timer.isTooShort()).isTrue();
        assertThat(timer.stop()).isEmpty();

        verify(sessions, never()).log(any());
        assertThat(timer.stateProperty().get()).isEqualTo(SessionTimer.State.PAUSED);
        timer.discard();
        assertThat(timer.isActive()).isFalse();
    }

    @Test
    void secondsRoundToTheNearestMinute() {
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofSeconds(89));
        timer.stop();

        assertThat(savedSession().minutes()).isEqualTo(1);
    }

    @Test
    void aSecondSessionCannotStartWhileOneIsActive() {
        timer.start(target, SessionTimer.Mode.FREE);

        assertThatThrownBy(() -> timer.start(target, SessionTimer.Mode.FREE)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aPomodoroMovesToABreakAndTheBreakIsNotStudyTime() {
        List<SessionTimer.Notice> notices = new ArrayList<>();
        timer.onNotice(notices::add);
        timer.start(target, SessionTimer.Mode.POMODORO);
        clock.advance(Duration.ofMinutes(25));
        timer.tick();

        assertThat(timer.phase()).isEqualTo(SessionTimer.Phase.BREAK);
        assertThat(timer.completedBlocks()).isEqualTo(1);
        assertThat(notices).extracting(SessionTimer.Notice::kind).containsExactly(SessionTimer.NoticeKind.BREAK_STARTED);

        clock.advance(Duration.ofMinutes(3));
        timer.tick();
        assertThat(timer.focusSeconds()).isEqualTo(25 * 60);

        clock.advance(Duration.ofMinutes(2));
        timer.tick();
        assertThat(timer.phase()).isEqualTo(SessionTimer.Phase.FOCUS);
        assertThat(timer.stateProperty().get()).isEqualTo(SessionTimer.State.PAUSED);
        assertThat(notices).extracting(SessionTimer.Notice::kind)
                .containsExactly(SessionTimer.NoticeKind.BREAK_STARTED, SessionTimer.NoticeKind.FOCUS_READY);

        timer.resume();
        clock.advance(Duration.ofMinutes(10));
        timer.stop();

        assertThat(savedSession().minutes()).isEqualTo(35);
    }

    @Test
    void aPomodoroThatSleptThroughPhasesCatchesUp() {
        timer.start(target, SessionTimer.Mode.POMODORO);
        clock.advance(Duration.ofMinutes(25 + 5 + 7));

        timer.tick();

        assertThat(timer.completedBlocks()).isEqualTo(1);
        assertThat(timer.phase()).isEqualTo(SessionTimer.Phase.FOCUS);
        assertThat(timer.stateProperty().get()).isEqualTo(SessionTimer.State.PAUSED);
        assertThat(timer.focusSeconds()).isEqualTo(25 * 60);
    }

    @Test
    void theTextsFollowThePomodoro() {
        timer.start(target, SessionTimer.Mode.POMODORO);
        clock.advance(Duration.ofMinutes(10));
        timer.tick();

        assertThat(timer.phaseTextProperty().get()).isEqualTo("Focus 1");
        assertThat(timer.remainingTextProperty().get()).isEqualTo("15:00 left");
    }

    @Test
    void aSessionLeftRunningIsOfferedAtTheNextStart() {
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofMinutes(42));
        SessionTimer nextRun = newTimer(); // the app was closed: a new timer reads the saved copy

        Optional<SessionTimer.Recovery> found = nextRun.pendingRecovery();

        assertThat(found).isPresent();
        assertThat(found.get().minutes()).isEqualTo(42);
        assertThat(found.get().target()).isEqualTo(target);
        assertThat(found.get().startedAt()).isEqualTo(Instant.parse("2026-10-28T18:00:00Z"));
    }

    @Test
    void aRecoveredSessionCanBeResumedOrDiscarded() {
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofMinutes(30));

        SessionTimer resumed = newTimer();
        resumed.resumeRecovered();
        clock.advance(Duration.ofMinutes(5));
        assertThat(resumed.isActive()).isTrue();
        assertThat(resumed.focusSeconds()).isEqualTo(35 * 60);

        SessionTimer discarded = newTimer();
        discarded.discardRecovered();
        assertThat(preferences.getSavedTimer()).isEmpty();
    }

    @Test
    void aLeftoverUnderAMinuteIsDroppedWithoutAsking() {
        timer.start(target, SessionTimer.Mode.FREE);
        clock.advance(Duration.ofSeconds(20));

        assertThat(newTimer().pendingRecovery()).isEmpty();
        assertThat(preferences.getSavedTimer()).isEmpty();
    }

    @Test
    void nothingIsOfferedWhenNothingWasRunning() {
        assertThat(timer.pendingRecovery()).isEmpty();
    }
}
