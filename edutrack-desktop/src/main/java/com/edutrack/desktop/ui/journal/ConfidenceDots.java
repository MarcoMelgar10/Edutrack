package com.edutrack.desktop.ui.journal;

import com.edutrack.desktop.ui.common.I18n;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/** Five small dots, the first {@code level} of them filled: how confident a check-in says the learner felt. */
final class ConfidenceDots extends HBox {

    static final int DOTS = 5;
    private static final PseudoClass FILLED = PseudoClass.getPseudoClass("filled");

    ConfidenceDots() {
        super(3);
        getStyleClass().add("confidence-dots");
        setAlignment(Pos.CENTER_LEFT);
        for (int index = 0; index < DOTS; index++) {
            Region dot = new Region();
            dot.getStyleClass().add("confidence-dot");
            getChildren().add(dot);
        }
        setFocusTraversable(false);
    }

    /** Fills the first {@code level} dots (1 to 5); {@code null} or 0 leaves all of them empty. */
    void show(Integer level) {
        int filled = level == null ? 0 : level;
        for (int index = 0; index < DOTS; index++) {
            getChildren().get(index).pseudoClassStateChanged(FILLED, index < filled);
        }
        setAccessibleText(I18n.get("journal.confidence.dots", filled));
    }
}
