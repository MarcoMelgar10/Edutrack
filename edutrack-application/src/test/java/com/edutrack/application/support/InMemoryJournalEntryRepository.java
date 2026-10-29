package com.edutrack.application.support;

import com.edutrack.domain.journal.JournalEntry;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalEntryRepository;
import com.edutrack.domain.journal.JournalFilter;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InMemoryJournalEntryRepository implements JournalEntryRepository {

    private final Map<JournalEntryId, JournalEntry> entries = new LinkedHashMap<>();

    @Override
    public JournalEntry save(JournalEntry entry) {
        entries.put(entry.id(), entry);
        return entry;
    }

    @Override
    public Optional<JournalEntry> findById(JournalEntryId id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public void delete(JournalEntryId id) {
        entries.remove(id);
    }

    @Override
    public List<JournalEntry> find(JournalFilter filter) {
        return entries.values().stream()
                .filter(entry -> filter.areaId() == null || filter.areaId().equals(entry.areaId()))
                .filter(entry -> filter.goalId() == null || filter.goalId().equals(entry.goalId()))
                .filter(entry -> filter.kind() == null || filter.kind() == entry.kind())
                .sorted(Comparator.comparing(JournalEntry::writtenOn).thenComparing(JournalEntry::createdAt).reversed())
                .toList();
    }

    @Override
    public List<JournalEntry> findWrittenBetween(LocalDate from, LocalDate to) {
        return entries.values().stream()
                .filter(entry -> !entry.writtenOn().isBefore(from) && !entry.writtenOn().isAfter(to))
                .sorted(Comparator.comparing(JournalEntry::writtenOn).thenComparing(JournalEntry::createdAt))
                .toList();
    }
}
