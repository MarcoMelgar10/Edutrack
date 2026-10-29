package com.edutrack.desktop;

import javafx.application.Application;

/**
 * Process entry point.
 *
 * <p>Kept separate from {@link JavaFxApplication} so the app can start from the classpath
 * (fat jar, {@code spring-boot:run}): the JavaFX launcher refuses to start when the main class
 * itself extends {@link Application} and JavaFX is not on the module path.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        Application.launch(JavaFxApplication.class, args);
    }
}
