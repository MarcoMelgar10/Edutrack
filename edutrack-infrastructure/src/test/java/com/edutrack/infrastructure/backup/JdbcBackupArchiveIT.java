package com.edutrack.infrastructure.backup;

import com.edutrack.application.backup.BackupInfo;
import com.edutrack.domain.shared.DomainValidationException;
import com.edutrack.infrastructure.SeedData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Backs up a database with a row in every table, wipes it and restores it: every table must come back exactly
 * as it was, down to arrays, microsecond instants and generated columns. Commits for real (the restore is one
 * transaction), so it cleans every table afterwards.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JdbcBackupArchive.class, JdbcBackupArchiveIT.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JdbcBackupArchiveIT {

    private static final Path FILES_ROOT = filesRoot();

    @TestConfiguration
    static class Config {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-28T20:15:30Z"), ZoneOffset.UTC);
        }
    }

    @DynamicPropertySource
    static void filesDirectory(DynamicPropertyRegistry registry) {
        registry.add("edutrack.files-dir", FILES_ROOT::toString);
    }

    @TempDir
    Path folder;

    @Autowired
    private JdbcBackupArchive archive;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() throws IOException {
        wipeEverything();
        SeedData.insert(jdbc);
        Path file = FILES_ROOT.resolve(SeedData.ATTACHMENT_PATH);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "hello world", StandardCharsets.UTF_8);
    }

    @AfterEach
    void clean() throws IOException {
        wipeEverything();
    }

    @Test
    void aWipedDatabaseComesBackIdentical() throws IOException {
        Map<String, List<String>> before = dump();
        Path zip = folder.resolve("backup.zip");

        BackupInfo written = archive.write(zip);
        wipeEverything();
        assertThat(jdbc.queryForObject("select count(*) from area", Integer.class)).isZero();
        archive.restore(zip);

        assertThat(dump()).isEqualTo(before);
        assertThat(written.tables()).containsEntry("card", 1).containsEntry("card_schedule", 2).containsEntry("area", 1);
        assertThat(written.files()).isEqualTo(1);
        assertThat(Files.readString(FILES_ROOT.resolve(SeedData.ATTACHMENT_PATH))).isEqualTo("hello world");
    }

    @Test
    void restoringReplacesWhatIsThereNow() throws IOException {
        Path zip = folder.resolve("backup.zip");
        archive.write(zip);
        jdbc.update("insert into area (id, name, icon, position, created_at) values (?, 'Extra', 'BOOK', 1, now())",
                SeedData.id(900));
        Files.writeString(FILES_ROOT.resolve("stray.txt"), "left behind");

        archive.restore(zip);

        assertThat(jdbc.queryForList("select name from area", String.class)).containsExactly("Idiomas");
        assertThat(FILES_ROOT.resolve("stray.txt")).doesNotExist();
        assertThat(FILES_ROOT.resolve(SeedData.ATTACHMENT_PATH)).exists();
    }

    @Test
    void readReportsTheManifestWithoutTouchingAnything() {
        Path zip = folder.resolve("backup.zip");
        archive.write(zip);

        BackupInfo info = archive.read(zip);

        assertThat(info.createdAt()).isEqualTo(Instant.parse("2026-10-28T20:15:30Z"));
        assertThat(info.schemaVersion()).isEqualTo(latestVersion());
        assertThat(info.tables()).containsKeys("area", "goal", "learning_record", "journal_entry", "attachment");
        assertThat(info.tables()).doesNotContainKey("flyway_schema_history");
    }

    @Test
    void aBackupFromANewerVersionIsRefusedAndNothingChanges() throws IOException {
        Path zip = zipWith(folder.resolve("newer.zip"), manifest("999", 1, "{}"), Map.of());
        Map<String, List<String>> before = dump();

        assertThatThrownBy(() -> archive.restore(zip))
                .isInstanceOfSatisfying(DomainValidationException.class, e -> assertThat(e.code()).isEqualTo("backup-newer"));

        assertThat(dump()).isEqualTo(before);
    }

    @Test
    void filesThatAreNotBackupsAreRefused() throws IOException {
        Path notAZip = Files.writeString(folder.resolve("notes.zip"), "just text");
        Path noManifest = zipWith(folder.resolve("empty.zip"), null, Map.of("data/area.json", "[]"));
        Path foreign = zipWith(folder.resolve("foreign.zip"),
                "{\"format\":1,\"app\":\"Other\",\"schemaVersion\":\"1\",\"createdAt\":\"2026-01-01T00:00:00Z\","
                        + "\"tables\":{},\"files\":0}", Map.of());
        Map<String, List<String>> before = dump();

        for (Path candidate : List.of(notAZip, noManifest, foreign)) {
            assertThatThrownBy(() -> archive.restore(candidate))
                    .as(candidate.getFileName().toString())
                    .isInstanceOfSatisfying(DomainValidationException.class,
                            e -> assertThat(e.code()).isEqualTo("backup-invalid"));
            assertThatThrownBy(() -> archive.read(candidate)).isInstanceOf(DomainValidationException.class);
        }
        assertThat(dump()).isEqualTo(before);
    }

    @Test
    void aDamagedTableRollsTheWholeRestoreBack() throws IOException {
        Path damaged = zipWith(folder.resolve("damaged.zip"), manifest(latestVersion(), 1, "{\"area\":1}"),
                Map.of("data/area.json", "[{\"id\":\"00000000-0000-0000-0000-000000000001\""));
        Map<String, List<String>> before = dump();

        assertThatThrownBy(() -> archive.restore(damaged)).isInstanceOf(DomainValidationException.class);

        assertThat(dump()).isEqualTo(before);
    }

    @Test
    void aBackupFromAnOlderVersionKeepsDefaultsForNewColumns() throws IOException {
        Path zip = folder.resolve("backup.zip");
        archive.write(zip);
        // Pretend the backup predates the weekly target column: drop it from goal.json before restoring.
        Path older = rewriteEntry(zip, folder.resolve("older.zip"), "data/goal.json",
                text -> text.replaceAll("\"weekly_target_minutes\":[^,}]+,?", "").replaceAll(",\\}", "}"));

        archive.restore(older);

        assertThat(jdbc.queryForObject("select weekly_target_minutes from goal", Integer.class)).isNull();
        assertThat(jdbc.queryForObject("select title from goal", String.class)).isEqualTo("Alpha del examen");
    }

    // ---------- helpers ----------

    private static Path filesRoot() {
        try {
            return Files.createTempDirectory("edutrack-files-it").toAbsolutePath().normalize();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String latestVersion() {
        return jdbc.queryForObject("select max(version::int)::text from flyway_schema_history where success", String.class);
    }

    private List<String> tableNames() {
        return jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = current_schema() and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                order by table_name
                """, String.class);
    }

    private void wipeEverything() throws IOException {
        jdbc.execute("truncate table " + String.join(", ", tableNames().stream().map(t -> '"' + t + '"').toList())
                + " cascade");
        try (var paths = Files.walk(FILES_ROOT)) {
            for (Path path : paths.sorted(Collections.reverseOrder()).toList()) {
                if (!path.equals(FILES_ROOT)) {
                    Files.delete(path);
                }
            }
        }
    }

    /** Every table as sorted JSON rows (generated columns included), so equal dumps mean identical data. */
    private Map<String, List<String>> dump() {
        Map<String, List<String>> tables = new LinkedHashMap<>();
        for (String table : tableNames()) {
            List<String> rows = new ArrayList<>(
                    jdbc.queryForList("select to_jsonb(t)::text from \"" + table + "\" t", String.class));
            Collections.sort(rows);
            tables.put(table, rows);
        }
        return tables;
    }

    private static String manifest(String version, int format, String tables) {
        return "{\"format\":" + format + ",\"app\":\"EduTrack\",\"schemaVersion\":\"" + version
                + "\",\"createdAt\":\"2026-10-28T20:15:30Z\",\"tables\":" + tables + ",\"files\":0}";
    }

    private static Path zipWith(Path target, String manifest, Map<String, String> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            if (manifest != null) {
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return target;
    }

    private static Path rewriteEntry(Path source, Path target, String name, UnaryOperator<String> change)
            throws IOException {
        try (ZipFile in = new ZipFile(source.toFile());
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {
            for (ZipEntry entry : Collections.list(in.entries())) {
                out.putNextEntry(new ZipEntry(entry.getName()));
                byte[] bytes = in.getInputStream(entry).readAllBytes();
                if (entry.getName().equals(name)) {
                    bytes = change.apply(new String(bytes, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);
                }
                out.write(bytes);
                out.closeEntry();
            }
        }
        return target;
    }
}
