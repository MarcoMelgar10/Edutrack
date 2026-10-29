package com.edutrack.infrastructure.persistence;

import com.edutrack.domain.area.Area;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.area.AreaRepository;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.GoalRepository;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.journal.EntryDetails;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.journal.JournalEntry;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalEntryRepository;
import com.edutrack.domain.journal.JournalFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaAreaRepositoryAdapter.class, JpaGoalRepositoryAdapter.class, JpaJournalEntryRepositoryAdapter.class})
class JpaJournalEntryRepositoryAdapterIT {

    /** Microsecond precision, which is what PostgreSQL stores, so round-trips compare exactly. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00.123456Z");
    private static final Instant LATER = Instant.parse("2026-10-28T11:00:00.000001Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Autowired
    private AreaRepository areas;

    @Autowired
    private GoalRepository goals;

    @Autowired
    private JournalEntryRepository entries;

    @Autowired
    private TestEntityManager entityManager;

    private Area area;
    private Goal goal;
    private StepId step;

    private void givenAnAreaGoalAndStep() {
        area = areas.save(Area.create("English " + UUID.randomUUID(), null, AreaIcon.BOOK, 0, NOW));
        goal = Goal.create(area.id(), "Reach B2", null, null, 0, NOW);
        step = goal.addStep(new StepDetails("Grammar", null, null), StepStatus.NOT_STARTED, NOW).id();
        goals.save(goal);
        entityManager.flush();
    }

    private JournalEntry note(LocalDate on, String body, AreaId areaId, GoalId goalId, StepId stepId, Instant createdAt) {
        return JournalEntry.create(EntryKind.NOTE, new EntryDetails(on, null, body, areaId, goalId, stepId, null),
                null, TODAY, createdAt);
    }

    private JournalEntry checkIn(LocalDate on, int confidence, GoalId goalId) {
        return JournalEntry.create(EntryKind.CHECK_IN, new EntryDetails(on, "Week recap", null, null, goalId, null, confidence),
                240, TODAY, NOW);
    }

    private void flushAndClear() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void savesAndLoadsALinkedNoteWithAllItsFields() {
        givenAnAreaGoalAndStep();
        JournalEntry entry = JournalEntry.create(EntryKind.NOTE,
                new EntryDetails(TODAY, "Idea", "# Shadowing\n\n  indented\n", area.id(), goal.id(), step, null),
                null, TODAY, NOW);

        JournalEntry saved = entries.save(entry);
        flushAndClear();
        JournalEntry loaded = entries.findById(entry.id()).orElseThrow();

        assertThat(saved).usingRecursiveComparison().isEqualTo(entry);
        assertThat(loaded).usingRecursiveComparison().isEqualTo(entry);
        assertThat(loaded.body()).isEqualTo("# Shadowing\n\n  indented\n");
        assertThat(loaded.confidence()).isNull();
        assertThat(loaded.weekMinutes()).isNull();
    }

    @Test
    void savesAndLoadsACheckInWithConfidenceAndWeekSnapshot() {
        givenAnAreaGoalAndStep();
        JournalEntry entry = checkIn(TODAY, 4, goal.id());

        entries.save(entry);
        flushAndClear();
        JournalEntry loaded = entries.findById(entry.id()).orElseThrow();

        assertThat(loaded).usingRecursiveComparison().isEqualTo(entry);
        assertThat(loaded.kind()).isEqualTo(EntryKind.CHECK_IN);
        assertThat(loaded.confidence()).isEqualTo(4);
        assertThat(loaded.weekMinutes()).isEqualTo(240);
        assertThat(loaded.body()).isEmpty();
        assertThat(loaded.areaId()).isNull();
    }

    @Test
    void findByIdIsEmptyForAnUnknownEntry() {
        assertThat(entries.findById(JournalEntryId.newId())).isEmpty();
    }

    @Test
    void savingAnEditedEntryUpdatesTheSameRow() {
        givenAnAreaGoalAndStep();
        JournalEntry entry = note(TODAY, "first", null, null, null, NOW);
        entries.save(entry);
        flushAndClear();

        JournalEntry loaded = entries.findById(entry.id()).orElseThrow();
        loaded.update(new EntryDetails(TODAY.minusDays(1), "Title", "second", area.id(), goal.id(), null, null), TODAY, LATER);
        entries.save(loaded);
        flushAndClear();

        JournalEntry reloaded = entries.findById(entry.id()).orElseThrow();
        assertThat(reloaded).usingRecursiveComparison().isEqualTo(loaded);
        assertThat(reloaded.updatedAt()).isEqualTo(LATER);
        assertThat(reloaded.createdAt()).isEqualTo(NOW);
        assertThat(count("select count(*) from journal_entry where id = :id", entry.id().value())).isEqualTo(1);
    }

    @Test
    void savingTheSameNewEntryTwiceKeepsOneRow() {
        JournalEntry entry = note(TODAY, "once", null, null, null, NOW);

        entries.save(entry);
        entries.save(entry);
        flushAndClear();

        assertThat(count("select count(*) from journal_entry where id = :id", entry.id().value())).isEqualTo(1);
    }

    @Test
    void deleteRemovesTheEntryAndDeletingAMissingOneDoesNothing() {
        JournalEntry entry = note(TODAY, "gone", null, null, null, NOW);
        JournalEntry kept = note(TODAY, "kept", null, null, null, NOW);
        entries.save(entry);
        entries.save(kept);
        flushAndClear();

        entries.delete(entry.id());
        entries.delete(JournalEntryId.newId());
        flushAndClear();

        assertThat(entries.findById(entry.id())).isEmpty();
        assertThat(entries.findById(kept.id())).isPresent();
    }

    @Test
    void findReturnsEntriesNewestFirstByWrittenDateThenCreation() {
        JournalEntry oldest = note(TODAY.minusDays(5), "oldest", null, null, null, LATER);
        JournalEntry sameDayEarlier = note(TODAY, "same day, earlier", null, null, null, NOW);
        JournalEntry sameDayLater = note(TODAY, "same day, later", null, null, null, LATER);
        JournalEntry middle = note(TODAY.minusDays(2), "middle", null, null, null, NOW);
        entries.save(oldest);
        entries.save(sameDayEarlier);
        entries.save(middle);
        entries.save(sameDayLater);
        flushAndClear();

        assertThat(entries.find(JournalFilter.ALL)).extracting(JournalEntry::id)
                .containsSubsequence(sameDayLater.id(), sameDayEarlier.id(), middle.id(), oldest.id());
    }

    @Test
    void findFiltersByAreaGoalAndKindAndCombinesThem() {
        givenAnAreaGoalAndStep();
        Area otherArea = areas.save(Area.create("Music " + UUID.randomUUID(), null, AreaIcon.BOOK, 1, NOW));
        Goal otherGoal = goals.save(Goal.create(otherArea.id(), "Guitar", null, null, 0, NOW));
        JournalEntry englishNote = note(TODAY, "english", area.id(), goal.id(), null, NOW);
        JournalEntry englishCheckIn = JournalEntry.create(EntryKind.CHECK_IN,
                new EntryDetails(TODAY, null, null, area.id(), goal.id(), null, 3), 100, TODAY, NOW);
        JournalEntry musicNote = note(TODAY, "music", otherArea.id(), otherGoal.id(), null, NOW);
        JournalEntry unlinked = note(TODAY, "unlinked", null, null, null, NOW);
        entries.save(englishNote);
        entries.save(englishCheckIn);
        entries.save(musicNote);
        entries.save(unlinked);
        flushAndClear();

        assertThat(entries.find(new JournalFilter(area.id(), null, null))).extracting(JournalEntry::id)
                .containsExactlyInAnyOrder(englishNote.id(), englishCheckIn.id());
        assertThat(entries.find(new JournalFilter(null, otherGoal.id(), null))).extracting(JournalEntry::id)
                .containsExactly(musicNote.id());
        assertThat(entries.find(new JournalFilter(null, null, EntryKind.CHECK_IN))).extracting(JournalEntry::id)
                .contains(englishCheckIn.id()).doesNotContain(englishNote.id(), musicNote.id(), unlinked.id());
        assertThat(entries.find(new JournalFilter(area.id(), goal.id(), EntryKind.NOTE))).extracting(JournalEntry::id)
                .containsExactly(englishNote.id());
        assertThat(entries.find(new JournalFilter(otherArea.id(), goal.id(), null))).isEmpty();
    }

    @Test
    void findWrittenBetweenIncludesBothEndDaysAndReturnsOldestFirst() {
        JournalEntry before = note(TODAY.minusDays(4), "before", null, null, null, NOW);
        JournalEntry first = note(TODAY.minusDays(3), "first", null, null, null, NOW);
        JournalEntry inside = note(TODAY.minusDays(1), "inside", null, null, null, NOW);
        JournalEntry last = note(TODAY, "last", null, null, null, NOW);
        entries.save(last);
        entries.save(before);
        entries.save(inside);
        entries.save(first);
        flushAndClear();

        assertThat(entries.findWrittenBetween(TODAY.minusDays(3), TODAY)).extracting(JournalEntry::id)
                .containsSubsequence(first.id(), inside.id(), last.id())
                .doesNotContain(before.id());
        assertThat(entries.findWrittenBetween(TODAY.minusDays(100), TODAY.minusDays(50))).isEmpty();
    }

    @Test
    void deletingTheGoalKeepsTheEntryAndClearsOnlyItsGoalAndStepLinks() {
        givenAnAreaGoalAndStep();
        JournalEntry entry = note(TODAY, "about the goal", area.id(), goal.id(), step, NOW);
        entries.save(entry);
        flushAndClear();

        goals.delete(goal.id());
        flushAndClear();

        JournalEntry reloaded = entries.findById(entry.id()).orElseThrow();
        assertThat(reloaded.goalId()).isNull();
        assertThat(reloaded.stepId()).isNull();
        assertThat(reloaded.areaId()).isEqualTo(area.id());
        assertThat(reloaded.body()).isEqualTo("about the goal");
    }

    @Test
    void deletingTheAreaKeepsTheEntryAndClearsAllItsLinks() {
        givenAnAreaGoalAndStep();
        JournalEntry entry = note(TODAY, "about the area", area.id(), goal.id(), step, NOW);
        entries.save(entry);
        flushAndClear();

        areas.delete(area.id());
        flushAndClear();

        JournalEntry reloaded = entries.findById(entry.id()).orElseThrow();
        assertThat(reloaded.areaId()).isNull();
        assertThat(reloaded.goalId()).isNull();
        assertThat(reloaded.stepId()).isNull();
    }

    @Test
    void deletingTheStepKeepsTheEntryAndItsOtherLinks() {
        givenAnAreaGoalAndStep();
        JournalEntry entry = note(TODAY, "about the step", area.id(), goal.id(), step, NOW);
        entries.save(entry);
        flushAndClear();

        Goal loaded = goals.findById(goal.id()).orElseThrow();
        loaded.removeStep(step);
        goals.save(loaded);
        flushAndClear();

        JournalEntry reloaded = entries.findById(entry.id()).orElseThrow();
        assertThat(reloaded.stepId()).isNull();
        assertThat(reloaded.goalId()).isEqualTo(goal.id());
        assertThat(reloaded.areaId()).isEqualTo(area.id());
    }

    // A failed statement aborts the PostgreSQL transaction, so each rejection gets its own test.

    @Test
    void theDatabaseRejectsANoteWithAConfidence() {
        assertThatThrownBy(() -> insert("NOTE", 3)).hasMessageContaining("journal_entry");
    }

    @Test
    void theDatabaseRejectsACheckInWithoutAConfidence() {
        assertThatThrownBy(() -> insert("CHECK_IN", null)).hasMessageContaining("journal_entry");
    }

    @Test
    void theDatabaseRejectsAConfidenceAboveFive() {
        assertThatThrownBy(() -> insert("CHECK_IN", 6)).hasMessageContaining("confidence");
    }

    private void insert(String kind, Integer confidence) {
        entityManager.getEntityManager()
                .createNativeQuery("insert into journal_entry (id, kind, written_on, confidence, created_at, updated_at) "
                        + "values (:id, :kind, current_date, " + (confidence == null ? "null" : confidence) + ", now(), now())")
                .setParameter("id", UUID.randomUUID())
                .setParameter("kind", kind)
                .executeUpdate();
    }

    private long count(String sql, UUID id) {
        return ((Number) entityManager.getEntityManager()
                .createNativeQuery(sql)
                .setParameter("id", id)
                .getSingleResult()).longValue();
    }
}
