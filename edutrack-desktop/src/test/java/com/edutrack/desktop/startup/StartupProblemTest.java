package com.edutrack.desktop.startup;

import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class StartupProblemTest {

    @Test
    void serverDownIsDetectedThroughTheCauseChain() {
        Throwable failure = new IllegalStateException("context failed",
                new SQLException("Connection to localhost:5432 refused", "08001",
                        new ConnectException("Connection refused")));

        assertThat(StartupProblem.from(failure).headline()).contains("can't reach PostgreSQL");
    }

    @Test
    void wrongPasswordPointsToTheConfigFile() {
        Throwable failure = new RuntimeException(new SQLException("password authentication failed", "28P01"));

        StartupProblem problem = StartupProblem.from(failure);

        assertThat(problem.headline()).contains("rejected the login");
        assertThat(problem.advice()).contains("application.properties");
    }

    @Test
    void missingPasswordAsksToAddIt() {
        Throwable failure = new SQLException("no password was provided", "08004");

        assertThat(StartupProblem.from(failure).advice()).contains("spring.datasource.password");
    }

    @Test
    void missingDatabasePointsToTheSetupScript() {
        Throwable failure = new SQLException("database \"edutrack\" does not exist", "3D000");

        assertThat(StartupProblem.from(failure).advice()).contains("setup-db.sql");
    }

    @Test
    void anythingElseFallsBackToTheGenericMessage() {
        StartupProblem problem = StartupProblem.from(new IllegalArgumentException("boom"));

        assertThat(problem.headline()).isEqualTo("EduTrack couldn't start.");
        assertThat(problem.advice()).contains("edutrack.log");
    }
}
