package com.edutrack.desktop.ui.common;

import javafx.scene.Node;
import javafx.util.Subscription;

/**
 * Keeps a section that shows saved data in step with {@link DataChanges} only while it is part of a
 * window. Pages are kept alive when the learner moves away, so a plain subscription would go on
 * reloading (and could outlive the goal it shows); here the subscription starts when the node joins a
 * scene, reloads once at that moment, and ends when it leaves.
 */
public final class WhileShown {

    private WhileShown() {
    }

    /** {@code reload} runs on every data change and each time the node is put in a scene. */
    public static void reload(Node node, DataChanges dataChanges, Runnable reload) {
        Subscription[] current = {Subscription.EMPTY};
        node.sceneProperty().subscribe(scene -> {
            current[0].unsubscribe();
            current[0] = Subscription.EMPTY;
            if (scene != null) {
                reload.run();
                current[0] = dataChanges.subscribe(reload);
            }
        });
    }
}
