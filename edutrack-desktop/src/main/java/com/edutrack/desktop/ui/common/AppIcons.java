package com.edutrack.desktop.ui.common;

import javafx.scene.image.Image;

import java.util.List;
import java.util.Objects;

/** The "ET" monogram in every size Windows may ask for (title bar, taskbar, Alt+Tab). */
public final class AppIcons {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private AppIcons() {
    }

    public static List<Image> all() {
        return java.util.Arrays.stream(SIZES)
                .mapToObj(size -> "/icons/app-icon-" + size + ".png")
                .map(path -> new Image(Objects.requireNonNull(AppIcons.class.getResource(path), path).toExternalForm()))
                .toList();
    }
}
