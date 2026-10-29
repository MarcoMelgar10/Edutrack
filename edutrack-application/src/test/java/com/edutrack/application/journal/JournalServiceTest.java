package com.edutrack.application.journal;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.support.InMemoryGoalRepository;
import com.edutrack.application.support.InMemoryJournalEntryRepository;
import com.edutrack.application.support.InMemoryStudySessionRepository;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.Goal;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepDetails;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.journal.EntryDetails;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalFilter;
import com.edutrack.domain.session.SessionDetails;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalServiceTest {

    /** A Wednesday; its Monday-based week started on 2026-10-26. */
    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final InMemoryGoalRepository goals = new InMemoryGoalRepository();
    private final InMemoryStudySessionRepository sessions = new InMemoryStudySessionRepository();
    private final InMemoryJournalEntryRepository entries = new InMemoryJournalEntryRepository();
    private final JournalService service = new JournalService(entries, sessions, goals, clock);

    private final AreaId areaId = AreaId.newId();
    private final Goal english = goals.save(Goal.create(areaId, "Pass B2", null, null, 0, NOW));
    private final StepId grammar = english.addStep(new StepDetails("Grammar", null, null), StepStatus.NOT_STARTED, NOW).id();
    private final Goal guitar = goals.save(Goal.create(AreaId.newId(), "Guitar", null, null, 1, NOW));

    private static EntryDetails note(LocalDate on, String body) {
        return new EntryDetails(on, null, body, null, null, null, null);
    }

    private static EntryDetails checkInOn(LocalDate on, int confidence) {
        return new EntryDetails(on, null, null, null, null, null, confidence);
    }

    private void studied(Goal goal, String instant, int minutes) {
        sessions.save(StudySession.create(new SessionDetails(goal.id(), null, null, Instant.parse(instant), minutes, null),
                SessionSource.MANUAL, NOW));
    }

    @Test
    void writeNoteSavesTheEntry() {
        JournalEntryView view = service.writeNote(new EntryDetails(TODAY, "  Idea ", "# Shadowing works", null, null, null, null));

        assertThat(view.kind()).isEqualTo(EntryKind.NOTE);
        assertThat(view.writtenOn()).isEqualTo(TODAY);
        assertThat(view.title()).isEqualTo("Idea");
        assertThat(view.body()).isEqualTo("# Shadowing works");
        assertThat(view.confidence()).isNull();
        assertThat(view.weekMinutes()).isNull();
        assertThat(view.createdAt()).isEqualTo(NOW);
        assertThat(entries.findById(view.id())).isPresent();
    }

    @Test
    void writeNoteRejectsInvalidEntriesWithoutSavingThem() {
        assertThatThrownBy(() -> service.writeNote(note(TODAY, " ")))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("required"));
        assertThatThrownBy(() -> service.writeNote(note(TODAY.plusDays(1), "text")))
                .isInstanceOfSatisfying(DomainValidationException.class, error -> assertThat(error.code()).isEqualTo("future-date"));
        assertThat(entries.find(JournalFilter.ALL)).isEmpty();
    }

    @Test
    void linkingAGoalAlsoLinksItsAreaAndResolvesTitles() {
        JournalEntryView view = service.writeNote(new EntryDetails(TODAY, null, "text", null, english.id(), grammar, null));

        assertThat(view.goalId()).isEqualTo(english.id());
        assertThat(view.goalTitle()).isEqualTo("Pass B2");
        assertThat(view.stepId()).isEqualTo(grammar);
        assertThat(view.stepTitle()).isEqualTo("Grammar");
        assertThat(view.areaId()).isEqualTo(areaId);
    }

    @Test
    void linkingOnlyAStepFindsItsGoal() {
        JournalEntryView view = service.writeNote(new EntryDetails(TODAY, null, "text", null, null, grammar, null));

        assertThat(view.goalId()).isEqualTo(english.id());
        assertThat(view.areaId()).isEqualTo(areaId);
    }

    @Test
    void linkingOnlyAnAreaKeepsItWithoutGoalOrStep() {
        JournalEntryView view = service.writeNote(new EntryDetails(TODAY, null, "text", areaId, null, null, null));

        assertThat(view.areaId()).isEqualTo(areaId);
        assertThat(view.goalId()).isNull();
        assertThat(view.goalTitle()).isNull();
    }

    @Test
    void unknownGoalsAndStepsAreNotFound() {
        assertThatThrownBy(() -> service.writeNote(new EntryDetails(TODAY, null, "text", null, GoalId.newId(), null, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.writeNote(new EntryDetails(TODAY, null, "text", null, guitar.id(), grammar, null)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.writeNote(new EntryDetails(TODAY, null, "text", null, null, StepId.newId(), null)))
                .isInstanceOf(NotFoundException.class);
        assertThat(entries.find(JournalFilter.ALL)).isEmpty();
    }

    @Test
    void checkInSnapshotsTheMinutesOfAllGoalsInTheWeek() {
        studied(english, "2026-10-25T20:00:00Z", 99);     // Sunday: previous week
        studied(english, "2026-10-26T08:00:00Z", 60);
        studied(guitar, "2026-10-28T09:00:00Z", 30);

        JournalEntryView view = service.checkIn(checkInOn(TODAY, 4), DayOfWeek.MONDAY);

        assertThat(view.kind()).isEqualTo(EntryKind.CHECK_IN);
        assertThat(view.confidence()).isEqualTo(4);
        assertThat(view.weekMinutes()).isEqualTo(90);
        assertThat(view.body()).isEmpty();
    }

    @Test
    void checkInOnAGoalOnlyCountsThatGoal() {
        studied(english, "2026-10-26T08:00:00Z", 60);
        studied(guitar, "2026-10-28T09:00:00Z", 30);

        JournalEntryView view = service.checkIn(
                new EntryDetails(TODAY, null, "Slow week", null, guitar.id(), null, 2), DayOfWeek.MONDAY);

        assertThat(view.weekMinutes()).isEqualTo(30);
        assertThat(view.goalTitle()).isEqualTo("Guitar");
    }

    @Test
    void checkInUsesTheWeekOfItsDateAndTheChosenWeekStart() {
        studied(english, "2026-10-25T20:00:00Z", 45);     // Sunday
        studied(english, "2026-10-26T08:00:00Z", 60);     // Monday

        // Written for Sunday the 25th: Monday-based week 19-25 Oct has no sessions of the 26th.
        assertThat(service.checkIn(checkInOn(LocalDate.of(2026, 10, 25), 3), DayOfWeek.MONDAY).weekMinutes())
                .isEqualTo(45);
        // Sunday-based week 25-31 Oct holds both.
        assertThat(service.checkIn(checkInOn(LocalDate.of(2026, 10, 25), 3), DayOfWeek.SUNDAY).weekMinutes())
                .isEqualTo(105);
    }

    @Test
    void weekMinutesPreviewsTheSnapshotACheckInWouldTake() {
        studied(english, "2026-10-27T09:00:00Z", 60);
        studied(guitar, "2026-10-28T09:00:00Z", 30);
        studied(english, "2026-10-20T09:00:00Z", 45);   // the week before

        assertThat(service.weekMinutes(TODAY, null, DayOfWeek.MONDAY)).isEqualTo(90);
        assertThat(service.weekMinutes(TODAY, english.id(), DayOfWeek.MONDAY)).isEqualTo(60);
        assertThat(service.weekMinutes(LocalDate.of(2026, 10, 21), english.id(), DayOfWeek.MONDAY)).isEqualTo(45);
    }

    @Test
    void checkInRequiresAConfidenceAndAPastDate() {
        assertThatThrownBy(() -> service.checkIn(new EntryDetails(TODAY, null, null, null, null, null, null), DayOfWeek.MONDAY))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.checkIn(checkInOn(TODAY.plusDays(1), 3), DayOfWeek.MONDAY))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.checkIn(new EntryDetails(null, null, null, null, null, null, 3), DayOfWeek.MONDAY))
                .isInstanceOf(DomainValidationException.class);
        assertThat(entries.find(JournalFilter.ALL)).isEmpty();
    }

    @Test
    void updateEditsTheEntryAndKeepsTheCheckInSnapshot() {
        studied(english, "2026-10-26T08:00:00Z", 60);
        JournalEntryView checkIn = service.checkIn(checkInOn(TODAY, 4), DayOfWeek.MONDAY);
        studied(english, "2026-10-27T08:00:00Z", 40);

        JournalEntryView updated = service.update(checkIn.id(), new EntryDetails(TODAY, "Recap", "Better", null, null, null, 5));

        assertThat(updated.title()).isEqualTo("Recap");
        assertThat(updated.body()).isEqualTo("Better");
        assertThat(updated.confidence()).isEqualTo(5);
        assertThat(updated.weekMinutes()).isEqualTo(60);
        assertThat(updated.kind()).isEqualTo(EntryKind.CHECK_IN);
    }

    @Test
    void updateWithAWeekStartTakesTheSnapshotAgain() {
        studied(english, "2026-10-26T08:00:00Z", 60);
        JournalEntryView checkIn = service.checkIn(checkInOn(TODAY, 4), DayOfWeek.MONDAY);
        studied(english, "2026-10-27T08:00:00Z", 40);

        JournalEntryView updated = service.update(checkIn.id(), checkInOn(TODAY, 4), DayOfWeek.MONDAY);

        assertThat(updated.weekMinutes()).isEqualTo(100);
    }

    @Test
    void updateOfANoteWithAWeekStartStaysWithoutMinutes() {
        JournalEntryView written = service.writeNote(note(TODAY, "first"));

        JournalEntryView updated = service.update(written.id(), note(TODAY, "second"), DayOfWeek.MONDAY);

        assertThat(updated.body()).isEqualTo("second");
        assertThat(updated.weekMinutes()).isNull();
    }

    @Test
    void updateOfAnUnknownEntryIsNotFoundAndInvalidChangesAreRejected() {
        JournalEntryView written = service.writeNote(note(TODAY, "first"));

        assertThatThrownBy(() -> service.update(JournalEntryId.newId(), note(TODAY, "x"))).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.update(written.id(), note(TODAY, " "))).isInstanceOf(DomainValidationException.class);
        assertThat(entries.findById(written.id()).orElseThrow().body()).isEqualTo("first");
    }

    @Test
    void deleteRemovesTheEntry() {
        JournalEntryView written = service.writeNote(note(TODAY, "first"));

        service.delete(written.id());

        assertThat(service.list(JournalFilter.ALL)).isEmpty();
    }

    @Test
    void listReturnsEntriesNewestFirstAndFiltersThem() {
        service.writeNote(note(TODAY.minusDays(3), "old note"));
        service.writeNote(new EntryDetails(TODAY.minusDays(1), null, "english note", null, english.id(), null, null));
        service.checkIn(checkInOn(TODAY, 3), DayOfWeek.MONDAY);
        service.writeNote(new EntryDetails(TODAY.minusDays(2), null, "guitar note", null, guitar.id(), null, null));

        assertThat(service.list(JournalFilter.ALL)).extracting(JournalEntryView::writtenOn)
                .containsExactly(TODAY, TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3));
        assertThat(service.list(new JournalFilter(null, null, EntryKind.CHECK_IN))).hasSize(1);
        assertThat(service.list(new JournalFilter(null, english.id(), null))).extracting(JournalEntryView::body)
                .containsExactly("english note");
        assertThat(service.list(new JournalFilter(areaId, null, EntryKind.NOTE))).extracting(JournalEntryView::body)
                .containsExactly("english note");
        assertThat(service.list(new JournalFilter(null, guitar.id(), EntryKind.CHECK_IN))).isEmpty();
    }

    @Test
    void theCheckInIsDueUntilOneIsWrittenSinceTheLastCheckInDay() {
        // The week ends on Sunday; the last Sunday was 2026-10-25.
        assertThat(service.checkInDue(DayOfWeek.SUNDAY)).isTrue();

        service.writeNote(note(TODAY, "a note is not a check-in"));
        assertThat(service.checkInDue(DayOfWeek.SUNDAY)).isTrue();

        service.checkIn(checkInOn(LocalDate.of(2026, 10, 24), 3), DayOfWeek.MONDAY);   // before the check-in day
        assertThat(service.checkInDue(DayOfWeek.SUNDAY)).isTrue();

        service.checkIn(checkInOn(LocalDate.of(2026, 10, 25), 3), DayOfWeek.MONDAY);
        assertThat(service.checkInDue(DayOfWeek.SUNDAY)).isFalse();
    }

    @Test
    void onTheCheckInDayItselfTheCheckInIsDueUntilWritten() {
        assertThat(service.checkInDue(DayOfWeek.WEDNESDAY)).isTrue();

        service.checkIn(checkInOn(TODAY, 3), DayOfWeek.MONDAY);

        assertThat(service.checkInDue(DayOfWeek.WEDNESDAY)).isFalse();
    }

    @Test
    void aCheckInFromThePreviousWeekDoesNotSatisfyThisWeek() {
        service.checkIn(checkInOn(LocalDate.of(2026, 10, 20), 3), DayOfWeek.MONDAY);

        assertThat(service.checkInDue(DayOfWeek.SUNDAY)).isTrue();
    }

    @Test
    void onlyCheckInsCountAsStudyDays() {
        service.writeNote(note(LocalDate.of(2026, 10, 26), "note"));
        service.checkIn(checkInOn(LocalDate.of(2026, 10, 27), 3), DayOfWeek.MONDAY);
        service.checkIn(checkInOn(LocalDate.of(2026, 10, 20), 3), DayOfWeek.MONDAY);

        Set<LocalDate> days = service.asStudyDaySource().studyDays(LocalDate.of(2026, 10, 25), TODAY);

        assertThat(days).containsExactly(LocalDate.of(2026, 10, 27));
    }

    @Test
    void listResolvesTitlesOfLinks() {
        service.writeNote(new EntryDetails(TODAY, null, "text", null, english.id(), grammar, null));

        List<JournalEntryView> views = service.list(JournalFilter.ALL);

        assertThat(views.getFirst().goalTitle()).isEqualTo("Pass B2");
        assertThat(views.getFirst().stepTitle()).isEqualTo("Grammar");
    }
}
