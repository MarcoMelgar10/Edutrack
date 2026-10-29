package com.edutrack.application.journal;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.progress.StudyDaySource;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.RoadmapStep;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.journal.EntryDetails;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.journal.JournalEntry;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalEntryRepository;
import com.edutrack.domain.journal.JournalFilter;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionRepository;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Use cases for the journal: free notes and the weekly check-in. A check-in keeps the minutes
 * studied in its week as they were when it was written. Framework-free; wired as a bean by the
 * desktop module.
 *
 * <p>Links are checked and completed here: a linked goal must exist, a linked step must belong to
 * it, and an entry linked to a goal (or step) is also linked to that goal's area.
 */
public class JournalService {

    private final JournalEntryRepository entries;
    private final StudySessionRepository sessions;
    private final GoalRepository goals;
    private final Clock clock;

    public JournalService(JournalEntryRepository entries, StudySessionRepository sessions, GoalRepository goals,
                          Clock clock) {
        this.entries = entries;
        this.sessions = sessions;
        this.goals = goals;
        this.clock = clock;
    }

    public JournalEntryView writeNote(EntryDetails details) {
        EntryDetails linked = withValidLinks(details);
        JournalEntry entry = JournalEntry.create(EntryKind.NOTE, linked, null, LocalDate.now(clock), clock.instant());
        return view(entries.save(entry));
    }

    /**
     * Writes the weekly check-in. Its week snapshot is the time studied in the week of
     * {@code writtenOn} (starting on {@code weekStart}): on the linked goal when there is one,
     * otherwise on every goal.
     */
    public JournalEntryView checkIn(EntryDetails details, DayOfWeek weekStart) {
        EntryDetails linked = withValidLinks(details);
        JournalEntry entry = JournalEntry.create(EntryKind.CHECK_IN, linked, weekMinutes(linked, weekStart),
                LocalDate.now(clock), clock.instant());
        return view(entries.save(entry));
    }

    /** Edits an entry; a check-in keeps the week snapshot it was written with. */
    public JournalEntryView update(JournalEntryId id, EntryDetails details) {
        JournalEntry entry = load(id);
        entry.update(withValidLinks(details), LocalDate.now(clock), clock.instant());
        return view(entries.save(entry));
    }

    /** Edits an entry and, for a check-in, takes the week snapshot again (its date or goal may have changed). */
    public JournalEntryView update(JournalEntryId id, EntryDetails details, DayOfWeek weekStart) {
        JournalEntry entry = load(id);
        EntryDetails linked = withValidLinks(details);
        Integer weekMinutes = entry.kind() == EntryKind.CHECK_IN ? weekMinutes(linked, weekStart) : null;
        entry.update(linked, weekMinutes, LocalDate.now(clock), clock.instant());
        return view(entries.save(entry));
    }

    public void delete(JournalEntryId id) {
        entries.delete(id);
    }

    /** Entries matching the filter, newest first. */
    public List<JournalEntryView> list(JournalFilter filter) {
        Map<GoalId, String> goalTitles = new HashMap<>();
        Map<StepId, String> stepTitles = new HashMap<>();
        titles(goalTitles, stepTitles);
        return entries.find(filter).stream()
                .map(entry -> JournalEntryView.of(entry, goalTitles, stepTitles))
                .toList();
    }

    /**
     * Whether the weekly check-in is waiting: the week ends on {@code checkInDay} (its most recent
     * occurrence up to today), and no check-in has been written since that day.
     */
    public boolean checkInDue(DayOfWeek checkInDay) {
        LocalDate today = LocalDate.now(clock);
        LocalDate since = today.with(TemporalAdjusters.previousOrSame(checkInDay));
        return entries.findWrittenBetween(since, today).stream().noneMatch(entry -> entry.kind() == EntryKind.CHECK_IN);
    }

    /** Every day with a check-in counts as a study day. */
    public StudyDaySource asStudyDaySource() {
        return (from, to) -> entries.findWrittenBetween(from, to).stream()
                .filter(entry -> entry.kind() == EntryKind.CHECK_IN)
                .map(JournalEntry::writtenOn)
                .collect(Collectors.toSet());
    }

    /**
     * The minutes studied in the week of {@code on} (starting on {@code weekStart}), on {@code goalId} or,
     * when that is {@code null}, on every goal: what a check-in written for that day would snapshot, so the
     * editor can show it before saving.
     */
    public int weekMinutes(LocalDate on, GoalId goalId, DayOfWeek weekStart) {
        ZoneId zone = clock.getZone();
        LocalDate first = on.with(TemporalAdjusters.previousOrSame(weekStart));
        return sessions.findStartedBetween(first.atStartOfDay(zone).toInstant(),
                        first.plusDays(7).atStartOfDay(zone).toInstant()).stream()
                .filter(session -> goalId == null || goalId.equals(session.goalId()))
                .mapToInt(StudySession::minutes)
                .sum();
    }

    private Integer weekMinutes(EntryDetails details, DayOfWeek weekStart) {
        if (details.writtenOn() == null) {
            return null; // the domain reports the missing date
        }
        return weekMinutes(details.writtenOn(), details.goalId(), weekStart);
    }

    private EntryDetails withValidLinks(EntryDetails details) {
        Objects.requireNonNull(details, "details");
        Goal goal = details.goalId() == null ? null
                : goals.findById(details.goalId()).orElseThrow(() -> new NotFoundException("Goal", details.goalId()));
        if (details.stepId() != null) {
            goal = goal != null ? requireStepOf(goal, details.stepId()) : goalOfStep(details.stepId());
        }
        return new EntryDetails(details.writtenOn(), details.title(), details.body(),
                goal == null ? details.areaId() : goal.areaId(), goal == null ? null : goal.id(),
                details.stepId(), details.confidence());
    }

    private static Goal requireStepOf(Goal goal, StepId stepId) {
        if (goal.steps().stream().map(RoadmapStep::id).noneMatch(stepId::equals)) {
            throw new NotFoundException("Step", stepId);
        }
        return goal;
    }

    private Goal goalOfStep(StepId stepId) {
        return goals.findAll().stream()
                .filter(goal -> goal.steps().stream().anyMatch(step -> step.id().equals(stepId)))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Step", stepId));
    }

    private JournalEntryView view(JournalEntry entry) {
        Map<GoalId, String> goalTitles = new HashMap<>();
        Map<StepId, String> stepTitles = new HashMap<>();
        titles(goalTitles, stepTitles);
        return JournalEntryView.of(entry, goalTitles, stepTitles);
    }

    private void titles(Map<GoalId, String> goalTitles, Map<StepId, String> stepTitles) {
        for (Goal goal : goals.findAll()) {
            goalTitles.put(goal.id(), goal.title());
            goal.steps().forEach(step -> stepTitles.put(step.id(), step.title()));
        }
    }

    private JournalEntry load(JournalEntryId id) {
        return entries.findById(id).orElseThrow(() -> new NotFoundException("Journal entry", id));
    }
}
