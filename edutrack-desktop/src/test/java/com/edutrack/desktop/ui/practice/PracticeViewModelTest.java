package com.edutrack.desktop.ui.practice;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.practice.PracticeCommands;
import com.edutrack.application.practice.PracticeService;
import com.edutrack.application.practice.RoutineStatus;
import com.edutrack.application.practice.RoutineView;
import com.edutrack.application.progress.GoalPace;
import com.edutrack.application.progress.PaceService;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.session.SessionTotals;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.I18n;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.session.StepChoice;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.Cadence;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.progress.Pace;
import com.edutrack.domain.shared.DomainValidationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PracticeViewModelTest {

    private final SessionService sessions = mock(SessionService.class);
    private final PaceService paceService = mock(PaceService.class);
    private final PracticeService practice = mock(PracticeService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final Messages messages = new Messages();
    private final GoalId goalId = GoalId.newId();
    private final AreaId areaId = AreaId.newId();
    private Preferences node;
    private PracticeViewModel viewModel;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("edutrack-practice-test-" + UUID.randomUUID());
        viewModel = new PracticeViewModel(sessions, paceService, practice, new UiPreferences(node), dataChanges, messages);
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    private GoalDetails goal(BigDecimal totalHours) {
        return new GoalDetails(goalId, areaId, "IELTS 7.0", null, null, com.edutrack.domain.progress.Progress.ZERO,
                BigDecimal.ZERO, totalHours, 0, false, List.of(), null, Instant.parse("2026-09-01T00:00:00Z"));
    }

    private RoutineView routine(Cadence cadence, int times, boolean active) {
        return new RoutineView(PracticeRoutineId.newId(), goalId, null, null, "G-C-D changes", cadence, times, 15, active, 0);
    }

    @Test
    void showsTheTimeLoggedAgainstTheEstimateAndTheLastWeek() {
        when(sessions.totals(goalId)).thenReturn(new SessionTotals(41 * 60, Map.of(), 210));
        when(paceService.pace(goalId)).thenReturn(new GoalPace(goalId, areaId, "IELTS 7.0",
                new Pace(Pace.PaceStatus.NO_PLAN, null, 0, null)));
        when(practice.forGoal(goalId)).thenReturn(List.of());
        when(practice.today(any())).thenReturn(List.of());

        viewModel.show(goal(new BigDecimal("196.0")));

        assertThat(viewModel.timeTextProperty().get()).isEqualTo("Logged 41 h · estimated 196 h · last 7 days 3.5 h");
    }

    @Test
    void leavesOutTheEstimateAndTheWeekWhenThereAreNone() {
        when(sessions.totals(goalId)).thenReturn(new SessionTotals(0, Map.of(), 0));
        when(paceService.pace(goalId)).thenReturn(new GoalPace(goalId, areaId, "x",
                new Pace(Pace.PaceStatus.NO_PLAN, null, 0, null)));
        when(practice.forGoal(goalId)).thenReturn(List.of());
        when(practice.today(any())).thenReturn(List.of());

        viewModel.show(goal(BigDecimal.ZERO));

        assertThat(viewModel.timeTextProperty().get()).isEqualTo("Logged 0 min");
        assertThat(viewModel.paceVisibleProperty().get()).isFalse();
    }

    @Test
    void wordsThePaceWithWhatIsNeededAndWhatIsDone() {
        Pace behind = new Pace(Pace.PaceStatus.BEHIND, 480, 180, LocalDate.of(2027, 3, 12));

        assertThat(viewModel.paceText(behind))
                .isEqualTo("Behind pace · needs 8 h/week · doing 3 h/week · finishes about 12 Mar");
        assertThat(viewModel.paceText(new Pace(Pace.PaceStatus.ON_TRACK, 300, 330, null)))
                .isEqualTo("On track · needs 5 h/week · doing 5.5 h/week");
        assertThat(viewModel.paceText(new Pace(Pace.PaceStatus.FINISHED, null, 0, null)))
                .isEqualTo("Every estimated hour is done");
    }

    @Test
    void aBehindPaceIsFlagged() {
        when(sessions.totals(goalId)).thenReturn(new SessionTotals(60, Map.of(), 60));
        when(paceService.pace(goalId)).thenReturn(new GoalPace(goalId, areaId, "x",
                new Pace(Pace.PaceStatus.BEHIND, 480, 60, null)));
        when(practice.forGoal(goalId)).thenReturn(List.of());
        when(practice.today(any())).thenReturn(List.of());

        viewModel.show(goal(new BigDecimal("10")));

        assertThat(viewModel.paceBehindProperty().get()).isTrue();
        assertThat(viewModel.paceVisibleProperty().get()).isTrue();
    }

    @Test
    void routinesShowTheirCadenceAndThisWeeksProgress() {
        RoutineView daily = routine(Cadence.DAILY, 7, true);
        RoutineView weekly = routine(Cadence.WEEKLY, 3, true);
        RoutineView paused = routine(Cadence.WEEKLY, 2, false);
        when(sessions.totals(goalId)).thenReturn(new SessionTotals(0, Map.of(), 0));
        when(paceService.pace(goalId)).thenReturn(new GoalPace(goalId, areaId, "x",
                new Pace(Pace.PaceStatus.NO_PLAN, null, 0, null)));
        when(practice.forGoal(goalId)).thenReturn(List.of(daily, weekly, paused));
        when(practice.today(any())).thenReturn(List.of(
                new RoutineStatus(daily, "IELTS 7.0", areaId, 15, 5, true, false),
                new RoutineStatus(weekly, "IELTS 7.0", areaId, 0, 3, false, true)));

        viewModel.show(goal(BigDecimal.ZERO));

        List<RoutineRow> rows = viewModel.routines();
        assertThat(rows).extracting(RoutineRow::cadenceText)
                .containsExactly("Every day · 15 min", "3× a week · 15 min", "2× a week · 15 min");
        assertThat(rows).extracting(RoutineRow::weekText)
                .containsExactly("5 of 7 days this week", "3 of 3 this week", "Paused");
        assertThat(rows.get(0).doneToday()).isTrue();
        assertThat(rows.get(1).metThisWeek()).isTrue();
        assertThat(viewModel.routinesTitleProperty().get()).isEqualTo("Practice routines (3)");
    }

    @Test
    void routineActionsGoThroughTheServiceAndAnnounceTheChange() {
        PracticeRoutineId id = PracticeRoutineId.newId();
        long[] changes = {0};
        dataChanges.subscribe(() -> changes[0]++);

        viewModel.pause(id);
        viewModel.resume(id);
        viewModel.delete(id);

        verify(practice).pause(id);
        verify(practice).resume(id);
        verify(practice).delete(id);
        assertThat(changes[0]).isEqualTo(3);
    }

    @Test
    void followsDataChangesWhileAGoalIsShownAndStopsWhenCleared() {
        when(sessions.totals(goalId)).thenReturn(new SessionTotals(0, Map.of(), 0));
        when(paceService.pace(goalId)).thenReturn(new GoalPace(goalId, areaId, "x",
                new Pace(Pace.PaceStatus.NO_PLAN, null, 0, null)));
        when(practice.forGoal(goalId)).thenReturn(List.of());
        when(practice.today(any())).thenReturn(List.of());
        viewModel.show(goal(BigDecimal.ZERO));

        when(sessions.totals(goalId)).thenReturn(new SessionTotals(90, Map.of(), 90));
        dataChanges.markChanged();
        assertThat(viewModel.timeTextProperty().get()).startsWith("Logged 1.5 h");

        viewModel.clear();
        when(sessions.totals(goalId)).thenReturn(new SessionTotals(600, Map.of(), 600));
        dataChanges.markChanged();
        assertThat(viewModel.timeTextProperty().get()).startsWith("Logged 1.5 h");
    }

    // ---- routine dialog ----

    @Test
    void theRoutineDialogNeedsATitleAndSensibleNumbers() {
        GoalService goals = mock(GoalService.class);
        RoutineDialogViewModel dialog = new RoutineDialogViewModel(practice, goals, messages);
        when(goals.getGoal(goalId)).thenReturn(goal(BigDecimal.ZERO));
        dialog.create(goalId);

        assertThat(dialog.validProperty().get()).isFalse();
        dialog.titleProperty().set("Scales");
        assertThat(dialog.validProperty().get()).isTrue();
        dialog.minutesProperty().set("0");
        assertThat(dialog.validProperty().get()).isFalse();
        dialog.minutesProperty().set("601");
        assertThat(dialog.validProperty().get()).isFalse();
        dialog.minutesProperty().set("30");
        dialog.cadenceProperty().set(dialog.cadences().get(1));
        dialog.timesPerWeekProperty().set("8");
        assertThat(dialog.validProperty().get()).isFalse();
        dialog.timesPerWeekProperty().set("4");
        assertThat(dialog.validProperty().get()).isTrue();
    }

    @Test
    void theRoutineDialogCreatesADailyRoutineWithSevenTimesAWeek() {
        GoalService goals = mock(GoalService.class);
        RoutineDialogViewModel dialog = new RoutineDialogViewModel(practice, goals, messages);
        when(goals.getGoal(goalId)).thenReturn(goal(BigDecimal.ZERO));
        RoutineView created = routine(Cadence.DAILY, 7, true);
        when(practice.create(any())).thenReturn(created);
        dialog.create(goalId);
        dialog.titleProperty().set("G-C-D changes");
        dialog.minutesProperty().set("15");
        dialog.timesPerWeekProperty().set("2"); // ignored for a daily routine

        RoutineView result = dialog.submit();

        assertThat(result).isSameAs(created);
        verify(practice).create(new PracticeCommands.CreateRoutine(goalId, null, "G-C-D changes", Cadence.DAILY, 7, 15));
        assertThat(dialog.title()).isEqualTo("New practice routine");
    }

    @Test
    void theRoutineDialogEditsAndShowsDomainErrors() {
        GoalService goals = mock(GoalService.class);
        RoutineDialogViewModel dialog = new RoutineDialogViewModel(practice, goals, messages);
        when(goals.getGoal(goalId)).thenReturn(goal(BigDecimal.ZERO));
        RoutineView existing = routine(Cadence.WEEKLY, 3, true);
        dialog.edit(existing);

        assertThat(dialog.titleProperty().get()).isEqualTo("G-C-D changes");
        assertThat(dialog.timesPerWeekProperty().get()).isEqualTo("3");
        assertThat(dialog.saveLabel()).isEqualTo("Save changes");
        when(practice.update(any())).thenThrow(new DomainValidationException("too-long", "Title is too long.", "Title", 120));

        assertThatThrownBy(dialog::submit).isInstanceOf(DomainValidationException.class);
        verify(practice).update(new PracticeCommands.UpdateRoutine(existing.id(), null, "G-C-D changes", Cadence.WEEKLY, 3, 15));
    }

    @Test
    void stepChoicesAreOfferedFromTheGoal() {
        GoalService goals = mock(GoalService.class);
        RoutineDialogViewModel dialog = new RoutineDialogViewModel(practice, goals, messages);
        when(goals.getGoal(goalId)).thenReturn(goal(BigDecimal.ZERO));

        dialog.create(goalId);

        assertThat(dialog.steps()).extracting(StepChoice::id).containsExactly((StepId) null);
        assertThat(I18n.language()).isNotNull();
    }
}
