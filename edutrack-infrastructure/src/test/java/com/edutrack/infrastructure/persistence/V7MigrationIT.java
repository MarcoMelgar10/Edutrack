package com.edutrack.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the real migrations in a scratch schema: V2 with an existing resource, then V7, which must
 * keep that resource, accept the new resource types and create the attachment and evidence tables.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class V7MigrationIT {

    private static final String SCHEMA = "v7_check";
    private static final String AREA = "00000000-0000-0000-0000-000000000001";
    private static final String GOAL = "00000000-0000-0000-0000-000000000002";
    private static final String STEP = "00000000-0000-0000-0000-000000000003";
    private static final String RECORD = "00000000-0000-0000-0000-000000000004";

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
    void keepsExistingResourcesAcceptsNewTypesAndCreatesTheNewTables() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway("2").clean();
        try {
            flyway("2").migrate();
            jdbc.update("insert into v7_check.area (id, name, icon, position, created_at) "
                    + "values (?::uuid, 'English', 'BOOK', 0, now())", AREA);
            jdbc.update("insert into v7_check.goal (id, area_id, title, position, created_at) "
                    + "values (?::uuid, ?::uuid, 'B2', 0, now())", GOAL, AREA);
            jdbc.update("insert into v7_check.roadmap_step (id, goal_id, position, title, status) "
                    + "values (?::uuid, ?::uuid, 0, 'Grammar', 'NOT_STARTED')", STEP, GOAL);
            jdbc.update("insert into v7_check.learning_record (id, step_id, notes, updated_at) "
                    + "values (?::uuid, ?::uuid, '', now())", RECORD, STEP);
            jdbc.update("insert into v7_check.learning_resource (id, record_id, position, title, url, type, reviewed) "
                    + "values (gen_random_uuid(), ?::uuid, 0, 'BBC', 'https://bbc.co.uk', 'ARTICLE', true)", RECORD);

            flyway("7").migrate();

            List<Map<String, Object>> resources = jdbc.queryForList(
                    "select title, url, type, reviewed from v7_check.learning_resource");
            assertThat(resources).hasSize(1);
            assertThat(resources.getFirst()).containsEntry("title", "BBC")
                    .containsEntry("type", "ARTICLE").containsEntry("reviewed", true);

            for (String type : List.of("PODCAST", "REPOSITORY", "TOOL", "COMMUNITY", "PERSON")) {
                insertResource(jdbc, type);
            }
            assertThat(jdbc.queryForObject("select count(*) from v7_check.learning_resource", Long.class)).isEqualTo(6L);
            assertThatThrownBy(() -> insertResource(jdbc, "NOT_A_TYPE")).isInstanceOf(DataIntegrityViolationException.class);

            // attachment: exactly one owner, positive size, unique stored path
            insertAttachment(jdbc, "a/one.txt", GOAL, null, 10);
            insertAttachment(jdbc, "a/two.txt", null, STEP, 10);
            assertThatThrownBy(() -> insertAttachment(jdbc, "a/both.txt", GOAL, STEP, 10))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertAttachment(jdbc, "a/none.txt", null, null, 10))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertAttachment(jdbc, "a/zero.txt", GOAL, null, 0))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> insertAttachment(jdbc, "a/one.txt", GOAL, null, 10))
                    .isInstanceOf(DataIntegrityViolationException.class);

            // evidence: kind check
            jdbc.update("insert into v7_check.evidence (id, goal_id, kind, title, taken_on, note, created_at) "
                    + "values (gen_random_uuid(), ?::uuid, 'PROJECT', 'Portfolio', current_date, 'ok', now())", GOAL);
            assertThatThrownBy(() -> jdbc.update("insert into v7_check.evidence (id, goal_id, kind, title, taken_on, note, created_at) "
                    + "values (gen_random_uuid(), ?::uuid, 'BOGUS', 'Portfolio', current_date, 'ok', now())", GOAL))
                    .isInstanceOf(DataIntegrityViolationException.class);
        } finally {
            flyway("7").clean();
        }
    }

    private static void insertResource(JdbcTemplate jdbc, String type) {
        jdbc.update("insert into v7_check.learning_resource (id, record_id, position, title, type) "
                + "values (gen_random_uuid(), ?::uuid, 1, ?, ?)", RECORD, "Resource " + type, type);
    }

    private static void insertAttachment(JdbcTemplate jdbc, String path, String goal, String step, long size) {
        jdbc.update("insert into v7_check.attachment (id, goal_id, step_id, original_name, stored_path, size_bytes, added_at) "
                + "values (gen_random_uuid(), ?::uuid, ?::uuid, 'file', ?, ?, now())", goal, step, path, size);
    }
}
