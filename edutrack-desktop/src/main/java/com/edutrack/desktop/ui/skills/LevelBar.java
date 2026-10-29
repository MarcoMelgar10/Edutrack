package com.edutrack.desktop.ui.skills;

import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

import java.util.function.IntConsumer;

/**
 * Five segments showing a competency's level: the reached ones are filled, the target one is outlined.
 * A click on a segment sets that level (clicking the only filled segment clears it); with the keyboard,
 * the digits 0 to 5 set the level and the arrows step it.
 */
final class LevelBar extends HBox {

    static final int SEGMENTS = 5;

    private final int level;
    private final IntConsumer onPick;

    LevelBar(int level, int target, IntConsumer onPick, String accessibleText) {
        super(4);
        this.level = level;
        this.onPick = onPick;
        getStyleClass().add("level-bar");
        setFocusTraversable(true);
        setAccessibleText(accessibleText);
        for (int segment = 1; segment <= SEGMENTS; segment++) {
            Region part = new Region();
            part.getStyleClass().add("level-segment");
            if (segment <= level) {
                part.getStyleClass().add("level-segment-filled");
            }
            if (segment == target) {
                part.getStyleClass().add("level-segment-target");
            }
            int picked = segment;
            part.setOnMouseClicked(click -> {
                if (click.getButton() == MouseButton.PRIMARY) {
                    pick(picked == level && picked == 1 ? 0 : picked);
                }
            });
            getChildren().add(part);
        }
        setOnKeyPressed(key -> {
            KeyCode code = key.getCode();
            if (code.isDigitKey() && code.getChar().length() == 1) {
                int digit = Integer.parseInt(code.getChar());
                if (digit <= SEGMENTS) {
                    pick(digit);
                    key.consume();
                }
            } else if (code == KeyCode.RIGHT || code == KeyCode.UP) {
                pick(Math.min(SEGMENTS, level + 1));
                key.consume();
            } else if (code == KeyCode.LEFT || code == KeyCode.DOWN) {
                pick(Math.max(0, level - 1));
                key.consume();
            }
        });
        Tooltip.install(this, new Tooltip(accessibleText));
    }

    private void pick(int next) {
        if (next != level) {
            onPick.accept(next);
        }
    }
}
