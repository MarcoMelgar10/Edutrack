package com.edutrack.desktop.ui.session;

import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.desktop.ui.theme.Motion;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.util.Subscription;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Binds {@code SessionTimerBarView.fxml} to the {@link SessionTimer}: the bar docked at the bottom of the
 * window while a session is active, with what is being studied (a link to it), the focus time, the pomodoro
 * phase and the Pause, Stop and Discard buttons. The shell calls {@link #dockInto(StackPane)} once and
 * {@link #dispose()} when it is replaced; the timer itself lives on.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SessionTimerBarController {

    private static final Duration NOTICE_TIME = Duration.seconds(12);

    private final SessionTimer timer;
    private final SessionLauncher launcher;
    private final Navigator navigator;
    private final UiPreferences preferences;
    private final Messages messages;

    @FXML
    private HBox root;
    @FXML
    private Hyperlink titleLink;
    @FXML
    private Label noticeLabel;
    @FXML
    private Label phaseLabel;
    @FXML
    private Label remainingLabel;
    @FXML
    private Label elapsedLabel;
    @FXML
    private Button pauseButton;
    @FXML
    private Button stopButton;
    @FXML
    private Button discardButton;

    private final FontIcon pauseIcon = new FontIcon(Feather.PAUSE);
    private final FontIcon playIcon = new FontIcon(Feather.PLAY);
    private final PauseTransition noticeTimeout = new PauseTransition(NOTICE_TIME);
    private Subscription subscriptions = Subscription.EMPTY;

    public SessionTimerBarController(SessionTimer timer, SessionLauncher launcher, Navigator navigator,
                                     UiPreferences preferences, Messages messages) {
        this.timer = timer;
        this.launcher = launcher;
        this.navigator = navigator;
        this.preferences = preferences;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        timer.refresh(); // the texts are built in the language of this window

        titleLink.textProperty().bind(Bindings.createStringBinding(
                () -> timer.targetProperty().get() == null ? "" : timer.targetProperty().get().title(),
                timer.targetProperty()));
        titleLink.setTooltip(new Tooltip(messages.get("session.bar.open")));
        phaseLabel.textProperty().bind(timer.phaseTextProperty());
        remainingLabel.textProperty().bind(timer.remainingTextProperty());
        elapsedLabel.textProperty().bind(timer.elapsedTextProperty());
        for (Label label : new Label[]{phaseLabel, remainingLabel}) {
            label.managedProperty().bind(label.textProperty().isNotEmpty());
            label.visibleProperty().bind(label.managedProperty());
        }
        noticeLabel.setVisible(false);
        noticeLabel.setManaged(false);
        noticeTimeout.setOnFinished(finished -> hideNotice());

        stopButton.setGraphic(new FontIcon(Feather.SQUARE));
        stopButton.setTooltip(new Tooltip(messages.get("session.stopAndSave.tooltip")));
        discardButton.setGraphic(new FontIcon(Feather.TRASH_2));
        discardButton.setTooltip(new Tooltip(messages.get("session.discard.tooltip")));
        discardButton.setAccessibleText(messages.get("session.discard.tooltip"));
        elapsedLabel.setAccessibleText(messages.get("session.elapsed"));

        subscriptions = subscriptions.and(timer.stateProperty().subscribe(this::showState));
        subscriptions = subscriptions.and(timer.onNotice(notice -> Platform.runLater(() -> showNotice(notice))));
    }

    /** Puts the bar into {@code dock} whenever a session is active and takes it out when none is. */
    public void dockInto(StackPane dock) {
        subscriptions = subscriptions.and(timer.stateProperty().subscribe(state -> {
            boolean docked = dock.getChildren().contains(root);
            if (state == SessionTimer.State.IDLE) {
                dock.getChildren().remove(root);
            } else if (!docked) {
                dock.getChildren().add(root);
                Motion.fadeIn(root);
            }
        }));
    }

    /** The bar's own view of the timer; unsubscribes so a replaced shell is not kept alive. */
    public void dispose() {
        subscriptions.unsubscribe();
        noticeTimeout.stop();
    }

    /** Asks about a session saved when the app last closed; call once the window is showing. */
    public void offerRecovery(Window owner) {
        launcher.offerRecovery(owner);
    }

    private void showState(SessionTimer.State state) {
        boolean paused = state == SessionTimer.State.PAUSED;
        pauseButton.setGraphic(paused ? playIcon : pauseIcon);
        String action = messages.get(paused ? "session.resume" : "session.pause");
        pauseButton.setText(action);
        pauseButton.setAccessibleText(action);
        if (state == SessionTimer.State.IDLE) {
            hideNotice();
        }
    }

    // ---- actions ----

    @FXML
    private void openTarget() {
        SessionTimer.Target target = timer.targetProperty().get();
        if (target == null) {
            return;
        }
        if (target.stepId() != null) {
            navigator.openStep(target.goalId(), target.stepId());
        } else {
            navigator.openGoal(target.goalId());
        }
    }

    @FXML
    private void togglePause() {
        if (timer.stateProperty().get() == SessionTimer.State.RUNNING) {
            timer.pause();
        } else if (timer.stateProperty().get() == SessionTimer.State.PAUSED) {
            hideNotice();
            timer.resume();
        }
    }

    @FXML
    private void stop() {
        launcher.stopAndSave(window());
    }

    @FXML
    private void discard() {
        launcher.discard(window());
    }

    // ---- pomodoro notices ----

    private void showNotice(SessionTimer.Notice notice) {
        noticeLabel.setText(notice.kind() == SessionTimer.NoticeKind.BREAK_STARTED
                ? messages.get("session.notice.breakStarted", preferences.getPomodoroBreakMinutes())
                : messages.get("session.notice.focusReady"));
        noticeLabel.setManaged(true);
        noticeLabel.setVisible(true);
        noticeTimeout.playFromStart();
        if (preferences.isNotificationsEnabled()) {
            java.awt.Toolkit.getDefaultToolkit().beep();
        }
    }

    private void hideNotice() {
        noticeTimeout.stop();
        noticeLabel.setVisible(false);
        noticeLabel.setManaged(false);
    }

    private Window window() {
        return root.getScene().getWindow();
    }
}
