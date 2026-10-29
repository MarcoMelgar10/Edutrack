package com.edutrack.domain.journal;

import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final Instant LATER = NOW.plusSeconds(3600);

    private static EntryDetails note(String body) {
        return new EntryDetails(TODAY, null, body, null, null, null, null);
    }

    private static EntryDetails checkIn(Integer confidence, String body) {
        return new EntryDetails(TODAY, null, body, null, null, null, confidence);
    }

    @Test
    void aNoteKeepsItsMarkdownExactlyAsWritten() {
        JournalEntry entry = JournalEntry.create(EntryKind.NOTE, note("  # Idea\n\n    code\n"), null, TODAY, NOW);

        assertThat(entry.body()).isEqualTo("  # Idea\n\n    code\n");
        assertThat(entry.kind()).isEqualTo(EntryKind.NOTE);
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void aNoteNeedsABody() {
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.NOTE, note("  \n "), null, TODAY, NOW))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("required"));
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.NOTE, note(null), null, TODAY, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void aNoteIgnoresConfidenceAndWeekMinutes() {
        JournalEntry entry = JournalEntry.create(EntryKind.NOTE,
                new EntryDetails(TODAY, null, "text", null, null, null, 4), 120, TODAY, NOW);

        assertThat(entry.confidence()).isNull();
        assertThat(entry.weekMinutes()).isNull();
    }

    @Test
    void bodyOf20001CharactersIsRejected() {
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.NOTE, note("a".repeat(20_001)), null, TODAY, NOW))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("too-long"));
        assertThat(JournalEntry.create(EntryKind.NOTE, note("a".repeat(20_000)), null, TODAY, NOW).body()).hasSize(20_000);
    }

    @Test
    void titleIsOptionalStrippedAndLimited() {
        EntryDetails titled = new EntryDetails(TODAY, "  Week recap  ", "text", null, null, null, null);
        EntryDetails blank = new EntryDetails(TODAY, "   ", "text", null, null, null, null);
        EntryDetails tooLong = new EntryDetails(TODAY, "a".repeat(121), "text", null, null, null, null);

        assertThat(JournalEntry.create(EntryKind.NOTE, titled, null, TODAY, NOW).title()).isEqualTo("Week recap");
        assertThat(JournalEntry.create(EntryKind.NOTE, blank, null, TODAY, NOW).title()).isNull();
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.NOTE, tooLong, null, TODAY, NOW))
                .isInstanceOf(DomainValidationException.class);
    }

    @Test
    void theDateIsRequiredAndCannotBeInTheFuture() {
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.NOTE,
                new EntryDetails(null, null, "text", null, null, null, null), null, TODAY, NOW))
                .hasMessageContaining("Date is required.");
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.NOTE,
                new EntryDetails(TODAY.plusDays(1), null, "text", null, null, null, null), null, TODAY, NOW))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                    assertThat(error.code()).isEqualTo("future-date");
                    assertThat(error.args()).containsExactly("Date");
                });
    }

    @Test
    void linksAreKept() {
        AreaId areaId = AreaId.newId();
        GoalId goalId = GoalId.newId();
        StepId stepId = StepId.newId();

        JournalEntry entry = JournalEntry.create(EntryKind.NOTE,
                new EntryDetails(TODAY, null, "text", areaId, goalId, stepId, null), null, TODAY, NOW);

        assertThat(entry.areaId()).isEqualTo(areaId);
        assertThat(entry.goalId()).isEqualTo(goalId);
        assertThat(entry.stepId()).isEqualTo(stepId);
    }

    @Test
    void aCheckInNeedsAConfidenceFromOneToFive() {
        assertThatThrownBy(() -> JournalEntry.create(EntryKind.CHECK_IN, checkIn(null, null), 0, TODAY, NOW))
                .hasMessageContaining("Confidence is required.");
        for (int confidence : new int[]{0, 6}) {
            assertThatThrownBy(() -> JournalEntry.create(EntryKind.CHECK_IN, checkIn(confidence, null), 0, TODAY, NOW))
                    .isInstanceOfSatisfying(DomainValidationException.class, error -> {
                        assertThat(error.code()).isEqualTo("out-of-range");
                        assertThat(error.args()).containsExactly("Confidence", 1, 5);
                    });
        }
        assertThat(JournalEntry.create(EntryKind.CHECK_IN, checkIn(1, null), 0, TODAY, NOW).confidence()).isEqualTo(1);
        assertThat(JournalEntry.create(EntryKind.CHECK_IN, checkIn(5, null), 0, TODAY, NOW).confidence()).isEqualTo(5);
    }

    @Test
    void aCheckInMayHaveNoReflectionAndKeepsTheWeekSnapshot() {
        JournalEntry entry = JournalEntry.create(EntryKind.CHECK_IN, checkIn(4, null), 210, TODAY, NOW);

        assertThat(entry.body()).isEmpty();
        assertThat(entry.confidence()).isEqualTo(4);
        assertThat(entry.weekMinutes()).isEqualTo(210);
    }

    @Test
    void updateKeepsTheKindCreationTimeAndWeekSnapshot() {
        JournalEntry entry = JournalEntry.create(EntryKind.CHECK_IN, checkIn(4, "good"), 210, TODAY, NOW);

        entry.update(new EntryDetails(TODAY.minusDays(1), "Recap", "better", null, null, null, 2), TODAY, LATER);

        assertThat(entry.kind()).isEqualTo(EntryKind.CHECK_IN);
        assertThat(entry.writtenOn()).isEqualTo(TODAY.minusDays(1));
        assertThat(entry.title()).isEqualTo("Recap");
        assertThat(entry.body()).isEqualTo("better");
        assertThat(entry.confidence()).isEqualTo(2);
        assertThat(entry.weekMinutes()).isEqualTo(210);
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void updateCanRefreshTheWeekSnapshot() {
        JournalEntry entry = JournalEntry.create(EntryKind.CHECK_IN, checkIn(4, null), 210, TODAY, NOW);

        entry.update(checkIn(4, null), 90, TODAY, LATER);

        assertThat(entry.weekMinutes()).isEqualTo(90);
    }

    @Test
    void aRejectedUpdateChangesNothing() {
        JournalEntry entry = JournalEntry.create(EntryKind.NOTE, note("original"), null, TODAY, NOW);

        assertThatThrownBy(() -> entry.update(note(" "), TODAY, LATER)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> entry.update(new EntryDetails(TODAY.plusDays(1), null, "changed", null, null, null, null),
                TODAY, LATER)).isInstanceOf(DomainValidationException.class);

        assertThat(entry.body()).isEqualTo("original");
        assertThat(entry.writtenOn()).isEqualTo(TODAY);
        assertThat(entry.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void restoreDoesNotCheckTheDateAgainstToday() {
        JournalEntry restored = JournalEntry.restore(JournalEntryId.newId(), EntryKind.NOTE, TODAY.plusDays(3), null,
                "text", null, null, null, null, null, NOW, NOW);

        assertThat(restored.writtenOn()).isEqualTo(TODAY.plusDays(3));
    }
}
