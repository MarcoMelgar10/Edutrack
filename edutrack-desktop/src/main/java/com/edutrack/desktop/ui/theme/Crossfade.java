package com.edutrack.desktop.ui.theme;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.transform.Transform;
import javafx.stage.Window;
import javafx.util.Duration;

/**
 * Hides a big change of the whole window (a new theme, a new language) behind a short crossfade:
 * a snapshot of the window is laid over it, the change happens underneath, and the snapshot fades out.
 * The scene root must be a {@link StackPane}; the overlay is appended to it, so the change should
 * replace children by position instead of clearing the pane.
 */
public final class Crossfade {

    private static final Duration DURATION = Duration.millis(220);

    private Crossfade() {
    }

    /** Runs {@code change} at once and fades the previous look out; without a showing window it just runs. */
    public static void run(Scene scene, Runnable change) {
        if (!(scene.getRoot() instanceof StackPane host) || host.getWidth() <= 0 || scene.getWindow() == null) {
            change.run();
            return;
        }

        ImageView overlay = new ImageView(snapshotAtScreenResolution(host, scene.getWindow()));
        overlay.setFitWidth(host.getWidth());
        overlay.setFitHeight(host.getHeight());
        overlay.setManaged(false);
        overlay.setMouseTransparent(true);
        host.getChildren().add(overlay);

        change.run();

        FadeTransition fade = new FadeTransition(DURATION, overlay);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_BOTH);
        fade.setOnFinished(done -> host.getChildren().remove(overlay));
        fade.play();
    }

    /** Snapshots at the monitor's output scale so the overlay is not blurry on HiDPI screens. */
    private static WritableImage snapshotAtScreenResolution(StackPane host, Window window) {
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setTransform(Transform.scale(window.getOutputScaleX(), window.getOutputScaleY()));
        return host.snapshot(parameters, null);
    }
}
