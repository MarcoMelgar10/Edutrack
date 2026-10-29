package com.edutrack.desktop.ui.journal;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.journal.JournalEntryView;
import com.edutrack.application.journal.JournalService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.journal.EntryDetails;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalFilter;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JournalViewModelTest {

    private static final Instant NOW = Instant.parse("2026-10-28T10:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final JournalService journal = mock(JournalService.class);
    private final AreaService areaService = mock(AreaService.class);
    private final GoalService goalService = mock(GoalService.class);
    private final UiPreferences preferences = mock(UiPreferences.class);
    private final DataChanges dataChanges = new DataChanges();

    private final AreaId englishId = AreaId.newId();
    private final AreaId musicId = AreaId.newId();
    private final GoalId ieltsId = GoalId.newId();
    private final GoalId guitarId = GoalId.newId();
    private final StepId listeningId = StepId.newId();
    private final List<JournalEntryView> stored = new ArrayList<>();

    private JournalViewModel viewModel;

    @BeforeEach
    void setUp() {
        when(preferences.getWeekStart()).thenReturn(DayOfWeek.MONDAY);
        when(areaService.listAreas()).thenReturn(List.of(area(englishId, "English"), area(musicId, "Music")));
        when(goalService.listGoals(englishId)).thenReturn(List.of(goal(ieltsId, englishId, "IELTS 7.0")));
        when(goalService.listGoals(musicId)).thenReturn(List.of(goal(guitarId, musicId, "Guitar")));
        when(goalService.getGoal(ieltsId)).thenReturn(ielts());
        when(journal.list(any())).thenAnswer(call -> {
            JournalFilter filter = call.getArgument(0);
            return stored.stream()
                    .filter(entry -> filter.kind() == null || entry.kind() == filter.kind())
                    .filter(entry -> filter.goalId() == null || filter.goalId().equals(entry.goalId()))
                    .filter(entry -> filter.areaId() == null || filter.areaId().equals(entry.areaId()))
                    .toList();
        });
        viewModel = new JournalViewModel(journal, areaService, goalService, preferences, dataChanges, clock,
                new Messages());
    }

    // ---- fixtures ----

    private static AreaSummary area(AreaId id, String name) {
        return new AreaSummary(id, name, "", AreaIcon.GLOBE, 1, Progress.ZERO);
    }

    private static GoalSummary goal(GoalId id, AreaId areaId, String title) {
        return new GoalSummary(id, areaId, title, null, Progress.ZERO, 1, 0, false);
    }

    private GoalDetails ielts() {
        StepView listening = new StepView(listeningId, 1, "Listening practice", null, new BigDecimal("10"),
                StepStatus.NOT_STARTED, null, RecordSummary.EMPTY);
        return new GoalDetails(ieltsId, englishId, "IELTS 7.0", null, null, Progress.ZERO, BigDecimal.ZERO,
                new BigDecimal("10"), 0, false, List.of(listening));
    }

    private JournalEntryView note(String title, String body, LocalDate on) {
        return new JournalEntryView(JournalEntryId.newId(), EntryKind.NOTE, on, title, body, null, null, null, null,
                null, null, null, NOW, NOW);
    }

    private JournalEntryView checkIn(LocalDate on, int confidence, int weekMinutes) {
        return new JournalEntryView(JournalEntryId.newId(), EntryKind.CHECK_IN, on, null, "", null, null, null, null,
                null, confidence, weekMinutes, NOW, NOW);
    }

    private JournalEntryView stored(JournalEntryView entry) {
        stored.add(entry);
        return entry;
    }

    private JournalViewModel freshViewModel() {
        return new JournalViewModel(journal, areaService, goalService, preferences, dataChanges, clock, new Messages());
    }

    // ---- the list ----

    @Test
    void listsTheEntriesAndSelectsTheFirstOne() {
        JournalEntryView newest = stored(note("Shadowing", "Works well", TODAY));
        stored(checkIn(TODAY.minusDays(3), 4, 390));

        viewModel = freshViewModel();

        assertThat(viewModel.entries()).hasSize(2);
        assertThat(viewModel.selectedProperty().get()).isEqualTo(newest);
        assertThat(viewModel.editingProperty().get()).isFalse();
    }

    @Test
    void anEmptyJournalSelectsNothing() {
        assertThat(viewModel.entries()).isEmpty();
        assertThat(viewModel.selectedProperty().get()).isNull();
    }

    @Test
    void theFiltersAreSentToTheService() {
        viewModel.areaFilterProperty().set(englishId);
        viewModel.goalFilterProperty().set(ieltsId);
        viewModel.kindFilterProperty().set(JournalViewModel.KindFilter.CHECK_INS);

        verify(journal).list(new JournalFilter(englishId, ieltsId, EntryKind.CHECK_IN));
        viewModel.kindFilterProperty().set(JournalViewModel.KindFilter.NOTES);
        verify(journal).list(new JournalFilter(englishId, ieltsId, EntryKind.NOTE));
    }

    @Test
    void theGoalFilterOffersTheGoalsOfTheChosenAreaAndForgetsAGoalOfAnotherArea() {
        assertThat(viewModel.filterGoals()).extracting(GoalSummary::title).containsExactly("IELTS 7.0", "Guitar");

        viewModel.areaFilterProperty().set(englishId);
        viewModel.goalFilterProperty().set(ieltsId);
        assertThat(viewModel.filterGoals()).extracting(GoalSummary::title).containsExactly("IELTS 7.0");

        viewModel.areaFilterProperty().set(musicId);
        assertThat(viewModel.filterGoals()).extracting(GoalSummary::title).containsExactly("Guitar");
        assertThat(viewModel.goalFilterProperty().get()).as("IELTS is not a music goal").isNull();
    }

    @Test
    void theSelectionSurvivesAReloadWhileTheEntryStillShows() {
        JournalEntryView first = stored(note("A", "a", TODAY));
        JournalEntryView second = stored(note("B", "b", TODAY.minusDays(1)));
        viewModel = freshViewModel();
        viewModel.selectedProperty().set(second);

        dataChanges.markChanged();

        assertThat(viewModel.selectedProperty().get()).isEqualTo(second);
        stored.remove(second);
        dataChanges.markChanged();
        assertThat(viewModel.selectedProperty().get()).as("the shown entry is gone: the first takes over")
                .isEqualTo(first);
    }

    @Test
    void stopsFollowingDataChangesWhenDisposed() {
        viewModel.dispose();
        stored(note("Late", "text", TODAY));

        dataChanges.markChanged();

        assertThat(viewModel.entries()).isEmpty();
    }

    // ---- how an entry reads ----

    @Test
    void theHeadlineIsTheTitleThenTheFirstLineWithoutMarkdownThenTheNameOfTheKind() {
        assertThat(viewModel.headline(note("Shadowing", "text", TODAY))).isEqualTo("Shadowing");
        assertThat(viewModel.headline(note(null, "\n\n## Idea for the week\nmore", TODAY))).isEqualTo("Idea for the week");
        assertThat(viewModel.headline(note(" ", "- practise every day", TODAY))).isEqualTo("practise every day");
        assertThat(viewModel.headline(checkIn(TODAY, 3, 60))).isEqualTo("Weekly check-in");
        assertThat(viewModel.headline(note(null, " ", TODAY))).isEqualTo("Untitled note");
    }

    @Test
    void aLongFirstLineIsCutWithAnEllipsis() {
        String headline = viewModel.headline(note(null, "x".repeat(200), TODAY));

        assertThat(headline).hasSize(80).endsWith("…");
    }

    @Test
    void theCaptionNamesTheGoalAndStepOrTheArea() {
        JournalEntryView onStep = new JournalEntryView(JournalEntryId.newId(), EntryKind.NOTE, TODAY, null, "x",
                englishId, ieltsId, "IELTS 7.0", listeningId, "Listening practice", null, null, NOW, NOW);
        JournalEntryView onGoal = new JournalEntryView(JournalEntryId.newId(), EntryKind.NOTE, TODAY, null, "x",
                englishId, ieltsId, "IELTS 7.0", null, null, null, null, NOW, NOW);
        JournalEntryView onArea = new JournalEntryView(JournalEntryId.newId(), EntryKind.NOTE, TODAY, null, "x",
                musicId, null, null, null, null, null, null, NOW, NOW);

        assertThat(viewModel.caption(onStep)).isEqualTo("IELTS 7.0 › Listening practice");
        assertThat(viewModel.caption(onGoal)).isEqualTo("IELTS 7.0");
        assertThat(viewModel.caption(onArea)).isEqualTo("Music");
        assertThat(viewModel.caption(note(null, "x", TODAY))).isEmpty();
    }

    @Test
    void aCheckInShowsItsWeekInHoursAndANoteShowsNone() {
        assertThat(viewModel.weekHours(checkIn(TODAY, 3, 390))).isEqualTo("6.5 h");
        assertThat(viewModel.weekHours(checkIn(TODAY, 3, 0))).isEqualTo("0 h");
        assertThat(viewModel.weekHours(note(null, "x", TODAY))).isEmpty();
    }

    // ---- writing a check-in ----

    @Test
    void aNewCheckInStartsFromTheTemplateDatedTodayAndNeedsAConfidence() {
        viewModel.startCheckIn();

        assertThat(viewModel.editingProperty().get()).isTrue();
        assertThat(viewModel.editingKindProperty().get()).isEqualTo(EntryKind.CHECK_IN);
        assertThat(viewModel.editorTitleProperty().get()).isEqualTo("Weekly check-in");
        assertThat(viewModel.editDateProperty().get()).isEqualTo(TODAY);
        assertThat(viewModel.editBodyProperty().get())
                .isEqualTo("### What went well\n\n### What was hard\n\n### Focus for next week\n");
        assertThat(viewModel.validProperty().get()).as("no confidence yet").isFalse();

        viewModel.editConfidenceProperty().set(4);
        assertThat(viewModel.validProperty().get()).isTrue();
        assertThat(viewModel.isDirty()).isTrue();
    }

    @Test
    void theWeekPreviewFollowsTheDateAndTheLinkedGoal() {
        when(journal.weekMinutes(TODAY, null, DayOfWeek.MONDAY)).thenReturn(390);
        when(journal.weekMinutes(TODAY, ieltsId, DayOfWeek.MONDAY)).thenReturn(120);
        when(journal.weekMinutes(TODAY.minusDays(7), ieltsId, DayOfWeek.MONDAY)).thenReturn(30);

        viewModel.startCheckIn();
        assertThat(viewModel.weekTextProperty().get()).isEqualTo("Studied that week: 6.5 h");

        viewModel.editGoalProperty().set(ieltsId);
        assertThat(viewModel.weekTextProperty().get()).isEqualTo("Studied that week: 2 h");

        viewModel.editDateProperty().set(TODAY.minusDays(7));
        assertThat(viewModel.weekTextProperty().get()).isEqualTo("Studied that week: 0.5 h");
    }

    @Test
    void aNoteHasNoWeekPreview() {
        viewModel.startNote(null);

        assertThat(viewModel.weekTextProperty().get()).isEmpty();
    }

    @Test
    void savingACheckInSendsTheConfidenceAndTheWeekStartAndShowsTheSavedEntry() {
        JournalEntryView saved = checkIn(TODAY, 4, 390);
        when(journal.checkIn(any(), eq(DayOfWeek.MONDAY))).thenAnswer(call -> stored(saved));
        viewModel.startCheckIn();
        viewModel.editConfidenceProperty().set(4);
        viewModel.editTitleProperty().set("  ");

        boolean ok = viewModel.save();

        assertThat(ok).isTrue();
        ArgumentCaptor<EntryDetails> sent = ArgumentCaptor.forClass(EntryDetails.class);
        verify(journal).checkIn(sent.capture(), eq(DayOfWeek.MONDAY));
        assertThat(sent.getValue().confidence()).isEqualTo(4);
        assertThat(sent.getValue().writtenOn()).isEqualTo(TODAY);
        assertThat(sent.getValue().body()).startsWith("### What went well");
        assertThat(viewModel.editingProperty().get()).isFalse();
        assertThat(viewModel.selectedProperty().get()).isEqualTo(saved);
        assertThat(viewModel.entries()).containsExactly(saved);
    }

    @Test
    void savingSignalsTheChangeSoTheStreakAndTheCheckInCardFollow() {
        JournalEntryView saved = checkIn(TODAY, 3, 0);
        when(journal.checkIn(any(), any())).thenReturn(saved);
        int[] changes = {0};
        dataChanges.subscribe(() -> changes[0]++);
        viewModel.startCheckIn();
        viewModel.editConfidenceProperty().set(3);

        viewModel.save();

        assertThat(changes[0]).isPositive();
    }

    // ---- writing a note ----

    @Test
    void aNoteNeedsTextBeforeItCanBeSaved() {
        viewModel.startNote(null);
        assertThat(viewModel.editorTitleProperty().get()).isEqualTo("New note");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.editBodyProperty().set("  \n ");
        assertThat(viewModel.validProperty().get()).isFalse();

        viewModel.editBodyProperty().set("Shadowing works");
        assertThat(viewModel.validProperty().get()).isTrue();

        viewModel.editDateProperty().set(null);
        assertThat(viewModel.validProperty().get()).as("a date is required").isFalse();
    }

    @Test
    void savingANoteSendsItsLinksAndDoesNotSendAConfidence() {
        JournalEntryView saved = note("Idea", "Shadowing works", TODAY);
        when(journal.writeNote(any())).thenAnswer(call -> stored(saved));
        viewModel.startNote(null);
        viewModel.editTitleProperty().set("Idea");
        viewModel.editBodyProperty().set("Shadowing works");
        viewModel.editGoalProperty().set(ieltsId);
        viewModel.editStepProperty().set(listeningId);
        viewModel.editConfidenceProperty().set(5);

        viewModel.save();

        ArgumentCaptor<EntryDetails> sent = ArgumentCaptor.forClass(EntryDetails.class);
        verify(journal).writeNote(sent.capture());
        assertThat(sent.getValue()).isEqualTo(new EntryDetails(TODAY, "Idea", "Shadowing works", englishId, ieltsId,
                listeningId, null));
    }

    @Test
    void aRejectedSaveKeepsTheEditorOpenWithTheTranslatedReason() {
        when(journal.writeNote(any())).thenThrow(
                new DomainValidationException("future-date", "Date cannot be in the future.", "Date"));
        viewModel.startNote(null);
        viewModel.editBodyProperty().set("text");

        boolean ok = viewModel.save();

        assertThat(ok).isFalse();
        assertThat(viewModel.editingProperty().get()).isTrue();
        assertThat(viewModel.errorProperty().get()).isEqualTo("Date cannot be in the future.");
    }

    @Test
    void aProblemFoundByTheViewIsShownInTheEditor() {
        viewModel.startNote(null);

        viewModel.fail(new DomainValidationException("invalid-date", "Date must be a date.", "Date", "12 Oct 2026"));

        assertThat(viewModel.errorProperty().get()).isEqualTo("Date must be a date such as 12 Oct 2026.");
    }

    // ---- links ----

    @Test
    void choosingAGoalAlsoChoosesItsAreaAndOffersItsSteps() {
        viewModel.startNote(null);
        assertThat(viewModel.editGoals()).extracting(GoalSummary::title).containsExactly("IELTS 7.0", "Guitar");

        viewModel.editGoalProperty().set(ieltsId);

        assertThat(viewModel.editAreaProperty().get()).isEqualTo(englishId);
        assertThat(viewModel.editGoals()).extracting(GoalSummary::title).containsExactly("IELTS 7.0");
        assertThat(viewModel.editSteps()).extracting(StepView::title).containsExactly("Listening practice");
    }

    @Test
    void changingTheAreaDropsAGoalAndStepThatAreNotInIt() {
        viewModel.startNote(ieltsId);
        viewModel.editStepProperty().set(listeningId);

        viewModel.editAreaProperty().set(musicId);

        assertThat(viewModel.editGoalProperty().get()).isNull();
        assertThat(viewModel.editStepProperty().get()).isNull();
        assertThat(viewModel.editSteps()).isEmpty();
        assertThat(viewModel.editGoals()).extracting(GoalSummary::title).containsExactly("Guitar");
    }

    @Test
    void aNoteAboutAGoalStartsLinkedToItAndItsArea() {
        viewModel.startNote(ieltsId);

        assertThat(viewModel.editGoalProperty().get()).isEqualTo(ieltsId);
        assertThat(viewModel.editAreaProperty().get()).isEqualTo(englishId);
        assertThat(viewModel.editSteps()).hasSize(1);
        assertThat(viewModel.isDirty()).as("a prepared link is not a change").isFalse();
    }

    // ---- editing, cancelling, deleting ----

    @Test
    void editingAnEntryLoadsItAndSavesItWithUpdate() {
        JournalEntryView existing = stored(checkIn(TODAY.minusDays(2), 2, 60));
        viewModel = freshViewModel();
        JournalEntryView updated = checkIn(TODAY.minusDays(2), 5, 60);
        when(journal.update(eq(existing.id()), any(), eq(DayOfWeek.MONDAY))).thenReturn(updated);

        viewModel.edit();

        assertThat(viewModel.editorTitleProperty().get()).isEqualTo("Edit check-in");
        assertThat(viewModel.isEditingExisting()).isTrue();
        assertThat(viewModel.editConfidenceProperty().get()).isEqualTo(2);
        assertThat(viewModel.editDateProperty().get()).isEqualTo(TODAY.minusDays(2));
        assertThat(viewModel.isDirty()).isFalse();

        viewModel.editConfidenceProperty().set(5);
        viewModel.save();

        ArgumentCaptor<EntryDetails> sent = ArgumentCaptor.forClass(EntryDetails.class);
        verify(journal).update(eq(existing.id()), sent.capture(), eq(DayOfWeek.MONDAY));
        assertThat(sent.getValue().confidence()).isEqualTo(5);
        assertThat(sent.getValue().writtenOn()).isEqualTo(TODAY.minusDays(2));
    }

    @Test
    void cancellingLeavesWithoutSaving() {
        viewModel.startNote(null);
        viewModel.editBodyProperty().set("half a thought");

        viewModel.cancel();

        assertThat(viewModel.editingProperty().get()).isFalse();
        assertThat(viewModel.isDirty()).isFalse();
        verify(journal, never()).writeNote(any());
    }

    @Test
    void anEditorWithUnsavedChangesIsNotReplacedByAnotherRequest() {
        viewModel.startNote(null);
        viewModel.editBodyProperty().set("half a thought");

        viewModel.startCheckIn();
        viewModel.startNote(ieltsId);

        assertThat(viewModel.editingKindProperty().get()).isEqualTo(EntryKind.NOTE);
        assertThat(viewModel.editBodyProperty().get()).isEqualTo("half a thought");
        assertThat(viewModel.editGoalProperty().get()).isNull();
    }

    @Test
    void aCleanEditorIsReplacedByTheNewRequest() {
        viewModel.startNote(null);

        viewModel.startCheckIn();

        assertThat(viewModel.editingKindProperty().get()).isEqualTo(EntryKind.CHECK_IN);
    }

    @Test
    void deletingTheSelectedEntryRemovesItAndSignalsTheChange() {
        JournalEntryView entry = stored(note("Old", "text", TODAY));
        viewModel = freshViewModel();
        org.mockito.Mockito.doAnswer(call -> stored.remove(entry)).when(journal).delete(entry.id());

        viewModel.deleteSelected();

        verify(journal).delete(entry.id());
        assertThat(viewModel.entries()).isEmpty();
        assertThat(viewModel.selectedProperty().get()).isNull();
    }
}
