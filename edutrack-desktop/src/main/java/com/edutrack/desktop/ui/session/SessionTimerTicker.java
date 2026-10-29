package com.edutrack.desktop.ui.session;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import org.springframework.stereotype.Component;

/**
 * Calls {@link SessionTimer#tick()} once a second while a session is running, so the clock text and
 * the pomodoro phases move. It holds no timing logic of its own: the timer works out everything from
 * the instants, so a late or missed tick (a sleeping laptop) never skews the minutes.
 */
@Component
public class SessionTimerTicker {

    private Timeline timeline;

    public SessionTimerTicker(SessionTimer timer) {
        timer.stateProperty().subscribe(state -> {
            if (state == SessionTimer.State.RUNNING) {
                if (timeline == null) {
                    timeline = new Timeline(new KeyFrame(Duration.seconds(1), tick -> timer.tick()));
                    timeline.setCycleCount(Animation.INDEFINITE);
                }
                timeline.play();
            } else if (timeline != null) {
                timeline.stop();
            }
        });
    }
}
