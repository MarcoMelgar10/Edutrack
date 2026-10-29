package com.edutrack.desktop.ui.theme;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.control.ProgressBar;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * The only animations EduTrack uses: short, quiet fades and progress bars that glide to their value.
 * Durations stay between 150 and 400 ms so motion never delays work.
 */
public final class Motion {

    public static final Duration FADE = Duration.millis(180);
    public static final Duration PROGRESS = Duration.millis(400);

    private static final String PROGRESS_ANIMATION_KEY = Motion.class.getName() + ".progress";

    private Motion() {
    }

    public static void fadeIn(Node node) {
        FadeTransition fade = new FadeTransition(FADE, node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        fade.play();
    }

    /** For dialogs: call when the window is about to show; it starts transparent and fades in. */
    public static void fadeIn(Window window) {
        window.setOpacity(0);
        new Timeline(new KeyFrame(FADE, new KeyValue(window.opacityProperty(), 1, Interpolator.EASE_OUT))).play();
    }

    /** Shows {@code value} (0–1) at once, cancelling a glide still running from a previous value. */
    public static void setProgress(ProgressBar bar, double value) {
        stopProgressAnimation(bar);
        bar.setProgress(value);
    }

    /** Moves the bar from its current value to {@code target} (0–1), replacing any running animation. */
    public static void animateProgress(ProgressBar bar, double target) {
        stopProgressAnimation(bar);
        if (bar.getProgress() < 0 || bar.getScene() == null) {
            bar.setProgress(target);
            return;
        }
        Timeline glide = new Timeline(new KeyFrame(PROGRESS,
                new KeyValue(bar.progressProperty(), target, Interpolator.EASE_BOTH)));
        bar.getProperties().put(PROGRESS_ANIMATION_KEY, glide);
        glide.play();
    }

    private static void stopProgressAnimation(ProgressBar bar) {
        if (bar.getProperties().remove(PROGRESS_ANIMATION_KEY) instanceof Timeline running) {
            running.stop();
        }
    }
}
