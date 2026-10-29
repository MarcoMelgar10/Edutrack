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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the real migrations in a scratch schema: V1 with a step that has a link, then V2, which must
 * turn that link into a resource of a new record and drop the old column.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class V2MigrationIT {

    private static final String SCHEMA = "migration_check";

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
    void movesStepLinksIntoResourcesAndDropsTheColumn() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        flyway("1").clean();
        try {
            flyway("1").migrate();
            jdbc.update("insert into migration_check.area (id, name, icon, position, created_at) "
                    + "values ('00000000-0000-0000-0000-000000000001', 'English', 'BOOK', 0, now())");
            jdbc.update("insert into migration_check.goal (id, area_id, title, position, created_at) "
                    + "values ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'B2', 0, now())");
            jdbc.update("insert into migration_check.roadmap_step (id, goal_id, position, title, resource_url, status) "
                    + "values ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 0, "
                    + "'With link', 'https://www.bbc.co.uk/learningenglish', 'NOT_STARTED')");
            jdbc.update("insert into migration_check.roadmap_step (id, goal_id, position, title, status) "
                    + "values ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000002', 1, "
                    + "'Without link', 'NOT_STARTED')");

            flyway("2").migrate();

            List<Map<String, Object>> resources = jdbc.queryForList(
                    "select r.step_id, x.title, x.url, x.type, x.reviewed, x.position "
                            + "from migration_check.learning_record r "
                            + "join migration_check.learning_resource x on x.record_id = r.id");
            assertThat(resources).hasSize(1);
            Map<String, Object> resource = resources.getFirst();
            assertThat(resource.get("step_id").toString()).isEqualTo("00000000-0000-0000-0000-000000000003");
            assertThat(resource.get("url")).isEqualTo("https://www.bbc.co.uk/learningenglish");
            assertThat(resource.get("title")).isEqualTo("bbc.co.uk/learningenglish");
            assertThat((String) resource.get("title")).doesNotContain("https://www.");
            assertThat(resource.get("type")).isEqualTo("OTHER");
            assertThat(resource.get("reviewed")).isEqualTo(false);
            assertThat(resource.get("position")).isEqualTo(0);

            assertThat(jdbc.queryForObject("select count(*) from migration_check.learning_record", Long.class))
                    .isEqualTo(1L);
            assertThat(jdbc.queryForObject("select count(*) from information_schema.columns "
                    + "where table_schema = ? and table_name = 'roadmap_step' and column_name = 'resource_url'",
                    Long.class, SCHEMA)).isZero();
        } finally {
            flyway("2").clean();
        }
    }
}
