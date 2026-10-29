package com.edutrack.domain.journal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Port for storing journal entries. Implemented by the infrastructure layer. */
public interface JournalEntryRepository {

    JournalEntry save(JournalEntry entry);

    Optional<JournalEntry> findById(JournalEntryId id);

    /** Deleting a missing entry does nothing. */
    void delete(JournalEntryId id);

    /** Entries matching the filter (links match exactly), newest first: by written date, then by creation. */
    List<JournalEntry> find(JournalFilter filter);

    /** Entries written on the days between {@code from} and {@code to}, both included, oldest first. */
    List<JournalEntry> findWrittenBetween(LocalDate from, LocalDate to);
}
