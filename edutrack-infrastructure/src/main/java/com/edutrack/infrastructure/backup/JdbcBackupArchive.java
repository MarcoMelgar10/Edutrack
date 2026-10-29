package com.edutrack.infrastructure.backup;

import com.edutrack.application.backup.BackupArchive;
import com.edutrack.application.backup.BackupInfo;
import com.edutrack.domain.shared.DomainValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Backs up the whole database and the attached files into one zip, and restores it.
 *
 * <p>Nothing about the tables is written down here: they are discovered from the catalog of the current
 * schema and sorted so that a table comes after the tables it refers to, so tables added by later migrations are
 * backed up without touching this class. Each table is stored as a JSON array of rows
 * ({@code data/<table>.json}); the values are text, except booleans and integers (numbers) and text arrays.
 *
 * <p>Restoring deletes every row and inserts the backup's rows in one transaction. Columns the backup does
 * not have keep their database default, so a backup from an older version restores into a newer database.
 * The attached files are unpacked next to the files folder first and only swapped in once the commit succeeded.
 */
@Component
public class JdbcBackupArchive implements BackupArchive {

    private static final int FORMAT = 1;
    private static final String APP = "EduTrack";
    private static final String MANIFEST = "manifest.json";
    private static final String DATA = "data/";
    private static final String FILES = "files/";
    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+)*");

    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager transactionManager;
    private final Clock clock;
    private final Path filesRoot;
    private final JsonMapper json = JsonMapper.builder().build();

    public JdbcBackupArchive(JdbcTemplate jdbc, PlatformTransactionManager transactionManager, Clock clock,
                             @Value("${edutrack.files-dir:${user.home}/.edutrack/files}") String filesDir) {
        this.jdbc = jdbc;
        this.transactionManager = transactionManager;
        this.clock = clock;
        this.filesRoot = Path.of(filesDir).toAbsolutePath().normalize();
    }

    // ---------- write ----------

    @Override
    public BackupInfo write(Path zip) {
        Path partial = zip.resolveSibling(zip.getFileName() + ".part");
        // One snapshot for the whole backup, so rows and counts agree even while the app keeps working.
        TransactionTemplate snapshot = new TransactionTemplate(transactionManager);
        snapshot.setReadOnly(true);
        snapshot.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        try {
            BackupInfo info = snapshot.execute(status -> writeArchive(partial));
            Files.move(partial, zip, StandardCopyOption.REPLACE_EXISTING);
            return info;
        } catch (IOException e) {
            deleteQuietly(partial);
            throw new UncheckedIOException("Cannot write " + zip, e);
        } catch (RuntimeException failure) {
            deleteQuietly(partial);
            throw failure;
        }
    }

    private BackupInfo writeArchive(Path target) {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (String table : tablesInDependencyOrder()) {
                zip.putNextEntry(new ZipEntry(DATA + table + ".json"));
                counts.put(table, writeTable(zip, table));
                zip.closeEntry();
            }
            int files = writeFiles(zip);
            BackupInfo info = new BackupInfo(latestVersion(), clock.instant(), counts, files);
            zip.putNextEntry(new ZipEntry(MANIFEST));
            writeManifest(zip, info);
            zip.closeEntry();
            return info;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot write " + target, e);
        }
    }

    private int writeTable(OutputStream zip, String table) {
        List<Column> columns = columnsOf(table);
        String select = columns.stream().map(Column::selectExpression).collect(Collectors.joining(", "));
        int[] rows = {0};
        try (JsonGenerator out = json.createGenerator(keepOpen(zip))) {
            out.writeStartArray();
            jdbc.query(connection -> {
                PreparedStatement statement = connection.prepareStatement("select " + select + " from " + quote(table));
                statement.setFetchSize(500);
                return statement;
            }, (ResultSet row) -> {
                out.writeStartObject();
                for (int index = 0; index < columns.size(); index++) {
                    out.writeName(columns.get(index).name());
                    writeValue(out, row, index + 1, columns.get(index));
                }
                out.writeEndObject();
                rows[0]++;
            });
            out.writeEndArray();
        }
        return rows[0];
    }

    private static void writeValue(JsonGenerator out, ResultSet row, int index, Column column) throws SQLException {
        switch (column.kind()) {
            case BOOLEAN -> {
                boolean value = row.getBoolean(index);
                if (row.wasNull()) {
                    out.writeNull();
                } else {
                    out.writeBoolean(value);
                }
            }
            case INTEGER -> {
                long value = row.getLong(index);
                if (row.wasNull()) {
                    out.writeNull();
                } else {
                    out.writeNumber(value);
                }
            }
            case ARRAY -> {
                Array array = row.getArray(index);
                if (array == null) {
                    out.writeNull();
                } else {
                    out.writeStartArray();
                    for (Object element : (Object[]) array.getArray()) {
                        if (element == null) {
                            out.writeNull();
                        } else {
                            out.writeString(element.toString());
                        }
                    }
                    out.writeEndArray();
                }
            }
            case TEXT -> {
                String value = row.getString(index);
                if (value == null) {
                    out.writeNull();
                } else {
                    out.writeString(value);
                }
            }
        }
    }

    /** Every file an attachment row points to, as {@code files/<storedPath>}; a missing file is skipped. */
    private int writeFiles(ZipOutputStream zip) throws IOException {
        int count = 0;
        for (String storedPath : jdbc.queryForList("select stored_path from attachment order by stored_path", String.class)) {
            Path file = filesRoot.resolve(storedPath).normalize();
            if (file.startsWith(filesRoot) && Files.isRegularFile(file)) {
                zip.putNextEntry(new ZipEntry(FILES + storedPath));
                Files.copy(file, zip);
                zip.closeEntry();
                count++;
            }
        }
        return count;
    }

    private void writeManifest(OutputStream zip, BackupInfo info) {
        try (JsonGenerator out = json.createGenerator(keepOpen(zip))) {
            out.writeStartObject();
            out.writeNumberProperty("format", FORMAT);
            out.writeStringProperty("app", APP);
            out.writeStringProperty("schemaVersion", info.schemaVersion());
            out.writeStringProperty("createdAt", info.createdAt().toString());
            out.writeObjectPropertyStart("tables");
            for (Map.Entry<String, Integer> table : info.tables().entrySet()) {
                out.writeNumberProperty(table.getKey(), table.getValue());
            }
            out.writeEndObject();
            out.writeNumberProperty("files", info.files());
            out.writeEndObject();
        }
    }

    // ---------- read ----------

    @Override
    public BackupInfo read(Path zip) {
        try (ZipFile archive = open(zip)) {
            return manifest(archive).info();
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + zip, e);
        }
    }

    private static ZipFile open(Path zip) throws IOException {
        try {
            return new ZipFile(zip.toFile());
        } catch (ZipException notAZip) {
            throw invalid();
        }
    }

    private record Manifest(BackupInfo info, int format) {
    }

    private Manifest manifest(ZipFile archive) throws IOException {
        ZipEntry entry = archive.getEntry(MANIFEST);
        if (entry == null) {
            throw invalid();
        }
        try (InputStream in = archive.getInputStream(entry)) {
            JsonNode root = json.readTree(in);
            if (!root.isObject() || !APP.equals(root.path("app").asString(null)) || !root.path("format").isIntegralNumber()) {
                throw invalid();
            }
            String version = root.path("schemaVersion").asString(null);
            if (version == null || !VERSION.matcher(version).matches() || !root.path("tables").isObject()
                    || !root.path("files").isIntegralNumber()) {
                throw invalid();
            }
            Map<String, Integer> tables = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> table : root.path("tables").properties()) {
                if (!table.getValue().isIntegralNumber()) {
                    throw invalid();
                }
                tables.put(table.getKey(), table.getValue().intValue());
            }
            Instant createdAt = Instant.parse(root.path("createdAt").asString(""));
            return new Manifest(new BackupInfo(version, createdAt, tables, root.path("files").intValue()),
                    root.path("format").intValue());
        } catch (JacksonException | DateTimeParseException damaged) {
            throw invalid();
        }
    }

    // ---------- restore ----------

    @Override
    public void restore(Path zip) {
        Path staging = filesRoot.resolveSibling(filesRoot.getFileName() + ".restore-" + UUID.randomUUID());
        boolean committed = false;
        try (ZipFile archive = open(zip)) {
            Manifest manifest = manifest(archive);
            String current = latestVersion();
            if (manifest.format() > FORMAT || compareVersions(manifest.info().schemaVersion(), current) > 0) {
                throw new DomainValidationException("backup-newer",
                        "This backup was made by a newer version of EduTrack (database version "
                                + manifest.info().schemaVersion() + ", this one has " + current + ").",
                        manifest.info().schemaVersion(), current);
            }
            if (manifest.format() < 1) {
                throw invalid();
            }
            extractFiles(archive, staging);
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> replaceRows(archive, manifest.info()));
            committed = true;
            swapFiles(staging);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot restore " + zip, e);
        } finally {
            if (!committed) {
                deleteTreeQuietly(staging);
            }
        }
    }

    private void replaceRows(ZipFile archive, BackupInfo info) {
        List<String> tables = tablesInDependencyOrder();
        if (!tables.isEmpty()) {
            jdbc.execute("truncate table " + tables.stream().map(JdbcBackupArchive::quote)
                    .collect(Collectors.joining(", ")) + " cascade");
        }
        for (String table : tables) {
            ZipEntry entry = archive.getEntry(DATA + table + ".json");
            if (entry == null) {
                continue;
            }
            JsonNode rows;
            try (InputStream in = archive.getInputStream(entry)) {
                rows = json.readTree(in);
            } catch (IOException | JacksonException damaged) {
                throw invalid();
            }
            if (!rows.isArray() || info.tables().containsKey(table) && info.tables().get(table) != rows.size()) {
                throw invalid();
            }
            insertRows(table, rows);
        }
    }

    private void insertRows(String table, JsonNode rows) {
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Column> known = new HashMap<>();
        columnsOf(table).forEach(column -> known.put(column.name(), column));
        // The columns the backup has and the database still has, in the order of the first row.
        List<Column> columns = new ArrayList<>();
        for (String name : rows.get(0).propertyNames()) {
            if (known.containsKey(name)) {
                columns.add(known.get(name));
            }
        }
        if (columns.isEmpty()) {
            throw invalid();
        }
        String sql = "insert into " + quote(table) + " (" + columns.stream().map(column -> quote(column.name()))
                .collect(Collectors.joining(", ")) + ") values (" + columns.stream()
                .map(column -> "cast(? as " + quote(column.udt()) + ")").collect(Collectors.joining(", ")) + ")";
        jdbc.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement statement, int row) throws SQLException {
                JsonNode values = rows.get(row);
                for (int index = 0; index < columns.size(); index++) {
                    bind(statement, index + 1, columns.get(index), values.path(columns.get(index).name()));
                }
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    private static void bind(PreparedStatement statement, int index, Column column, JsonNode value) throws SQLException {
        if (value.isNull() || value.isMissingNode()) {
            statement.setNull(index, Types.VARCHAR);
        } else if (value.isArray()) {
            List<String> elements = new ArrayList<>();
            value.forEach(element -> elements.add(element.isNull() ? null : element.asString()));
            statement.setArray(index, statement.getConnection().createArrayOf(column.elementType(), elements.toArray()));
        } else {
            statement.setString(index, value.asString());
        }
    }

    private void extractFiles(ZipFile archive, Path staging) throws IOException {
        Files.createDirectories(staging);
        for (ZipEntry entry : Collections.list(archive.entries())) {
            if (entry.isDirectory() || !entry.getName().startsWith(FILES)) {
                continue;
            }
            Path target = staging.resolve(entry.getName().substring(FILES.length())).normalize();
            if (!target.startsWith(staging) || target.equals(staging)) {
                throw invalid();
            }
            Files.createDirectories(target.getParent());
            try (InputStream in = archive.getInputStream(entry)) {
                Files.copy(in, target);
            }
        }
    }

    /** Replaces the contents of the files folder with the unpacked backup; the folder itself stays. */
    private void swapFiles(Path staging) throws IOException {
        if (filesRoot.getNameCount() < 2) {
            throw new IllegalStateException("Refusing to replace the files in " + filesRoot);
        }
        Files.createDirectories(filesRoot);
        try (Stream<Path> old = Files.list(filesRoot)) {
            for (Path path : old.toList()) {
                deleteTree(path);
            }
        }
        try (Stream<Path> restored = Files.list(staging)) {
            for (Path path : restored.toList()) {
                Files.move(path, filesRoot.resolve(path.getFileName()));
            }
        }
        deleteTree(staging);
    }

    // ---------- catalog ----------

    private record Column(String name, String udt) {

        enum Kind { TEXT, BOOLEAN, INTEGER, ARRAY }

        Kind kind() {
            if (udt.startsWith("_")) {
                return Kind.ARRAY;
            }
            return switch (udt) {
                case "bool" -> Kind.BOOLEAN;
                case "int2", "int4", "int8" -> Kind.INTEGER;
                default -> Kind.TEXT;
            };
        }

        /** The element type of an array column, as {@code createArrayOf} wants it. */
        String elementType() {
            return udt.substring(1);
        }

        /** Instants are written as ISO-8601 in UTC; everything else as PostgreSQL prints it. */
        String selectExpression() {
            String column = quote(name);
            return switch (udt) {
                case "timestamptz" -> "to_char(" + column + " at time zone 'UTC', 'YYYY-MM-DD\"T\"HH24:MI:SS.US\"Z\"')";
                case "bool", "int2", "int4", "int8" -> column;
                default -> kind() == Kind.ARRAY ? column : column + "::text";
            };
        }
    }

    /** The columns a row of the table carries: every one that is not computed by the database. */
    private List<Column> columnsOf(String table) {
        return jdbc.query("""
                select column_name, udt_name
                from information_schema.columns
                where table_schema = current_schema() and table_name = ? and is_generated <> 'ALWAYS'
                order by ordinal_position
                """, (row, index) -> new Column(row.getString(1), row.getString(2)), table);
    }

    /** The tables of the current schema (not Flyway's own), each after the tables it has foreign keys to. */
    private List<String> tablesInDependencyOrder() {
        Set<String> tables = new TreeSet<>(jdbc.queryForList("""
                select table_name from information_schema.tables
                where table_schema = current_schema() and table_type = 'BASE TABLE' and table_name <> ?
                """, String.class, HISTORY_TABLE));
        Map<String, Set<String>> parents = new HashMap<>();
        tables.forEach(table -> parents.put(table, new TreeSet<>()));
        jdbc.query("""
                select child.relname, parent.relname
                from pg_constraint c
                join pg_class child on child.oid = c.conrelid
                join pg_class parent on parent.oid = c.confrelid
                join pg_namespace schema on schema.oid = child.relnamespace
                where c.contype = 'f' and schema.nspname = current_schema()
                """, (RowCallbackHandler) row -> {
            String child = row.getString(1);
            String parent = row.getString(2);
            if (parents.containsKey(child) && parents.containsKey(parent) && !child.equals(parent)) {
                parents.get(child).add(parent);
            }
        });

        List<String> ordered = new ArrayList<>();
        Set<String> placed = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(tables);
        while (!pending.isEmpty()) {
            int before = pending.size();
            for (int left = before; left > 0; left--) {
                String table = pending.removeFirst();
                if (placed.containsAll(parents.get(table))) {
                    ordered.add(table);
                    placed.add(table);
                } else {
                    pending.addLast(table);
                }
            }
            if (pending.size() == before) {
                // A cycle of foreign keys: no order can satisfy it, so keep the rest in name order.
                pending.forEach(ordered::add);
                break;
            }
        }
        return ordered;
    }

    /** The newest migration Flyway applied, e.g. {@code "9"}. */
    private String latestVersion() {
        return jdbc.queryForList("select version from " + HISTORY_TABLE + " where success and version is not null",
                        String.class).stream()
                .max(JdbcBackupArchive::compareVersions)
                .orElseThrow(() -> new IllegalStateException("No migration has been applied"));
    }

    static int compareVersions(String first, String second) {
        String[] a = first.split("\\.");
        String[] b = second.split("\\.");
        for (int part = 0; part < Math.max(a.length, b.length); part++) {
            int result = Long.compare(part < a.length ? Long.parseLong(a[part]) : 0,
                    part < b.length ? Long.parseLong(b[part]) : 0);
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    // ---------- helpers ----------

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static DomainValidationException invalid() {
        return new DomainValidationException("backup-invalid", "This file is not a valid EduTrack backup.");
    }

    /** Lets Jackson finish a JSON document inside a zip entry without closing the whole zip. */
    private static OutputStream keepOpen(OutputStream target) {
        return new FilterOutputStream(target) {
            @Override
            public void write(byte[] bytes, int offset, int length) throws IOException {
                out.write(bytes, offset, length);
            }

            @Override
            public void close() throws IOException {
                flush();
            }
        };
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // a leftover .part file is harmless
        }
    }

    private static void deleteTreeQuietly(Path path) {
        try {
            deleteTree(path);
        } catch (IOException | RuntimeException ignored) {
            // best effort
        }
    }

    private static void deleteTree(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException failure) throws IOException {
                Files.delete(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
