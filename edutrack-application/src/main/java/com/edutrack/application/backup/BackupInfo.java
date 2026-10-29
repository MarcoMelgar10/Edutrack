package com.edutrack.application.backup;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a backup file says about itself: the database version it was taken from, when, how many rows
 * each table held and how many attached files it carries.
 */
public record BackupInfo(String schemaVersion, Instant createdAt, Map<String, Integer> tables, int files) {

    public BackupInfo {
        tables = Collections.unmodifiableMap(new LinkedHashMap<>(tables));
    }

    /** All rows of all tables. */
    public int rows() {
        return tables.values().stream().mapToInt(Integer::intValue).sum();
    }
}
