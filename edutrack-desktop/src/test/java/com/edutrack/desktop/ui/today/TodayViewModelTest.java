package com.edutrack.desktop.ui.today;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.AgendaItem;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.journal.JournalService;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.practice.RoutineStatus;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.application.progress.ConsistencyService;
import com.edutrack.application.progress.GoalPace;
import com.edutrack.application.progress.PaceService;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.progress.Pace;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.progress.StreakCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TodayViewModelTest {

    private final AreaService areas = mock(AreaService.class);
    private final GoalService goals = mock(GoalService.class);
    private final PracticeService practice = mock(PracticeService.class);
    private final PaceService pace = mock(PaceService.class);
    private final ConsistencyService consistency = mock(ConsistencyService.class);
    private final JournalService journal = mock(JournalService.class);
    private final ReviewService review = mock(ReviewService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC);
    private final AreaId areaId = AreaId.newId();
    private final GoalId goalId = GoalId.newId();
    private Preferences node;
    private UiPreferences preferences;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("edutrack-today-test-" + UUID.randomUUID());
        preferences = new UiPreferences(node);
        when(consistency.streak()).thenReturn(new StreakCalculator.Streak(0, 0, false));
        when(practice.today(any())).thenReturn(List.of());
        when(goals.agenda(any())).thenReturn(List.of());
        when(pace.all()).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    private TodayViewModel viewModel() {
        return new TodayViewModel(areas, goals, practice, pace, consistency, journal, review, preferences, dataChanges,
                clock, new Messages());
    }

    private void oneArea() {
        when(areas.listAreas()).thenReturn(List.of(new AreaSummary(areaId, "English", null, AreaIcon.GLOBE, 1, Progress.ZERO)));
        when(goals.listGoals(areaId)).thenReturn(List.of(new GoalSummary(goalId, areaId, "IELTS 7.0",
                LocalDate.of(2027, 3, 1), Progress.ZERO, 5, 0, false)));
    }

    @Test
    void withoutAreasThePageIsTheWelcomeAndNothingElse() {
        when(areas.listAreas()).thenReturn(List.of());

        TodayViewModel viewModel = viewModel();

        assertThat(viewModel.noAreasProperty().get()).isTrue();
        assertThat(viewModel.streakTextProperty().get()).isEmpty();
        assertThat(viewModel.checkInDueProperty().get()).isFalse();
        assertThat(viewModel.routines()).isEmpty();
    }

    @Test
    void theStreakTextSaysWhatIsGoingOn() {
        oneArea();
        TodayViewModel viewModel = viewModel();

        assertThat(viewModel.streakText(new StreakCalculator.Streak(0, 0, false), 0))
                .isEqualTo("Study today to start a streak.");
        assertThat(viewModel.streakText(new StreakCalculator.Streak(5, 12, true), 3))
                .isEqualTo("5-day streak · 3 study days this week · best 12");
        assertThat(viewModel.streakText(new StreakCalculator.Streak(1, 1, true), 1))
                .isEqualTo("1-day streak · 1 study day this week");
        assertThat(viewModel.streakText(new StreakCalculator.Streak(0, 4, false), 0))
                .isEqualTo("No streak right now · 0 study days this week · best 4");
    }

    @Test
    void showsTheCheckInAndTheCardsOnlyWhenTheyAreDue() {
        oneArea();
        when(journal.checkInDue(any())).thenReturn(true);
        when(review.dueCount(eq(ReviewScope.ALL), eq(20), eq(200))).thenReturn(18);

        TodayViewModel viewModel = viewModel();

        assertThat(viewModel.checkInDueProperty().get()).isTrue();
        assertThat(viewModel.reviewDueProperty().get()).isTrue();
        assertThat(viewModel.reviewTextProperty().get()).isEqualTo("18 cards to review");
    }

    @Test
    void hidesTheReviewWhenNothingIsDue() {
        oneArea();
        when(review.dueCount(any(), any(Integer.class), any(Integer.class))).thenReturn(0);

        assertThat(viewModel().reviewDueProperty().get()).isFalse();
    }

    @Test
    void routinesCarryTheirUsualMinutesForOneClickLogging() {
        oneArea();
        RoutineView daily = new RoutineView(PracticeRoutineId.newId(), goalId, null, null, "Scales", Cadence.DAILY, 7, 15,
                true, 0);
        when(practice.today(any())).thenReturn(List.of(new RoutineStatus(daily, "IELTS 7.0", areaId, 0, 2, false, false)));

        TodayViewModel viewModel = viewModel();
        TodayViewModel.RoutineItem item = viewModel.routines().getFirst();
        viewModel.logRoutine(item);

        assertThat(item.detailText()).isEqualTo("2 of 7 days this week");
        assertThat(item.doneToday()).isFalse();
        verify(practice).logPractice(daily.id(), 15, null);
    }

    @Test
    void theAgendaMarksOverdueStepsWithTheirDate() {
        oneArea();
        StepId step = StepId.newId();
        when(goals.agenda(any())).thenReturn(List.of(
                new AgendaItem(areaId, goalId, "IELTS 7.0", step, "Mock exam", LocalDate.of(2026, 10, 20),
                        StepStatus.NOT_STARTED, true),
                new AgendaItem(areaId, goalId, "IELTS 7.0", step, "Essay", LocalDate.of(2026, 10, 30),
                        StepStatus.IN_PROGRESS, false)));

        TodayViewModel viewModel = viewModel();

        assertThat(viewModel.agenda()).extracting(TodayViewModel.AgendaRow::dateText)
                .containsExactly("Overdue · 20 Oct", "Fri 30 Oct");
        assertThat(viewModel.agenda().getFirst().overdue()).isTrue();
    }

    @Test
    void onlyGoalsBehindPaceNeedAttention() {
        oneArea();
        when(pace.all()).thenReturn(List.of(
                new GoalPace(goalId, areaId, "IELTS 7.0", new Pace(Pace.PaceStatus.BEHIND, 480, 180, null)),
                new GoalPace(GoalId.newId(), areaId, "Guitar", new Pace(Pace.PaceStatus.ON_TRACK, 120, 130, null))));

        TodayViewModel viewModel = viewModel();

        assertThat(viewModel.attention()).hasSize(1);
        assertThat(viewModel.attention().getFirst().detailText()).isEqualTo("needs 8 h/week · doing 3 h/week");
    }

    @Test
    void goalsInProgressComeNearestDeadlineFirst() {
        oneArea();
        GoalId later = GoalId.newId();
        GoalId none = GoalId.newId();
        when(goals.listGoals(areaId)).thenReturn(List.of(
                new GoalSummary(none, areaId, "Someday", null, Progress.ZERO, 0, 0, false),
                new GoalSummary(later, areaId, "Later", LocalDate.of(2027, 6, 1), Progress.ZERO, 1, 0, false),
                new GoalSummary(goalId, areaId, "Soon", LocalDate.of(2026, 12, 1), Progress.ZERO, 1, 0, false),
                new GoalSummary(GoalId.newId(), areaId, "Done", null, Progress.COMPLETE, 1, 1, true)));

        assertThat(viewModel().goals()).extracting(TodayViewModel.GoalRow::title).containsExactly("Soon", "Later", "Someday");
    }

    @Test
    void refreshesWhenDataChanges() {
        oneArea();
        TodayViewModel viewModel = viewModel();
        assertThat(viewModel.goals()).hasSize(1);

        when(goals.listGoals(areaId)).thenReturn(List.of());
        dataChanges.markChanged();

        assertThat(viewModel.goals()).isEmpty();
        viewModel.dispose();
        when(goals.listGoals(areaId)).thenReturn(List.of(new GoalSummary(goalId, areaId, "Back", null, Progress.ZERO, 0, 0, false)));
        dataChanges.markChanged();
        assertThat(viewModel.goals()).isEmpty();
    }
}
