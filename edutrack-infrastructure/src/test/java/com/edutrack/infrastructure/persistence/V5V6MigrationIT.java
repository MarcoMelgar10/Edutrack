package com.edutrack.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real migrations in a scratch schema: a database at V2 with a goal, a step and a result
 * must come through V5 (the result gets no measure and no part) and V6 (cards) untouched.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class V5V6MigrationIT {

    private static final String SCHEMA = "v5_check";

    @Autowired
    private DataSource dataSource;

    private Flyway flyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(SCHEMA)
                .locations("classpath:db/migration")
                .target(target)
                .cleanDisabled(false)
                .load();
    }

    @Test
    void existingResultsSurviveV5WithoutAMeasureAndV6AddsCards() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway("2").clean();
        try {
            flyway("2").migrate();
            jdbc.update("insert into v5_check.area (id, name, icon, position, created_at) "
                    + "values ('00000000-0000-0000-0000-000000000001', 'English', 'BOOK', 0, now())");
            jdbc.update("insert into v5_check.goal (id, area_id, title, position, created_at) "
                    + "values ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'B2', 0, now())");
            jdbc.update("insert into v5_check.learning_record (id, goal_id, notes, updated_at) "
                    + "values ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', '', now())");
            jdbc.update("insert into v5_check.learning_result (id, record_id, position, title, score, taken_on) "
                    + "values ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000003', 0, 'Mock', 6.5, date '2026-03-01')");

            flyway("5").migrate();

            Map<String, Object> result = jdbc.queryForMap("select * from v5_check.learning_result");
            assertThat(result.get("title")).isEqualTo("Mock");
            assertThat(result.get("measure_id")).isNull();
            assertThat(result.get("part")).isNull();
            assertThat(jdbc.queryForObject("select count(*) from v5_check.measure", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from v5_check.competency", Long.class)).isZero();

            flyway("6").migrate();

            assertThat(jdbc.queryForObject("select count(*) from v5_check.card", Long.class)).isZero();
            assertThat(jdbc.queryForObject("select count(*) from information_schema.tables where table_schema = ? "
                    + "and table_name in ('card', 'card_schedule', 'review_log')", Long.class, SCHEMA)).isEqualTo(3L);
        } finally {
            flyway("6").clean();
        }
    }
}
