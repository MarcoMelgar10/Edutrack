package com.edutrack.desktop.startup;

import com.edutrack.desktop.ui.common.I18n;

import java.net.ConnectException;
import java.sql.SQLException;

/**
 * Turns a start-up exception into a message a person can act on, in the language of the interface
 * (the texts are {@code startup.*} bundle entries).
 *
 * <p>Only JDK types are inspected ({@link SQLException#getSQLState()}, {@link ConnectException}),
 * so the desktop module needs no compile-time dependency on the PostgreSQL driver.
 */
public record StartupProblem(String headline, String advice) {

    static final String CONFIG_FILE = "%USERPROFILE%\\.edutrack\\application.properties";
    static final String LOG_FILE = "%USERPROFILE%\\.edutrack\\logs\\edutrack.log";

    private static final String INVALID_PASSWORD = "28P01";
    private static final String PASSWORD_MISSING = "08004";
    private static final String DATABASE_MISSING = "3D000";

    public static StartupProblem from(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConnectException) {
                return new StartupProblem(I18n.get("startup.unreachable.headline"),
                        I18n.get("startup.unreachable.advice"));
            }
            if (cause instanceof SQLException sql && sql.getSQLState() != null) {
                switch (sql.getSQLState()) {
                    case INVALID_PASSWORD -> {
                        return new StartupProblem(I18n.get("startup.rejected.headline"),
                                I18n.get("startup.rejected.advice", CONFIG_FILE));
                    }
                    case PASSWORD_MISSING -> {
                        return new StartupProblem(I18n.get("startup.noPassword.headline"),
                                I18n.get("startup.noPassword.advice", CONFIG_FILE));
                    }
                    case DATABASE_MISSING -> {
                        return new StartupProblem(I18n.get("startup.noDatabase.headline"),
                                I18n.get("startup.noDatabase.advice"));
                    }
                    default -> {
                        // keep walking: a more specific cause may sit deeper in the chain
                    }
                }
            }
        }
        return new StartupProblem(I18n.get("startup.generic.headline"), I18n.get("startup.generic.advice", LOG_FILE));
    }
}
