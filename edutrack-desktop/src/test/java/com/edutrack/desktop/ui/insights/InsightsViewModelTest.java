package com.edutrack.desktop.ui.insights;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.insights.InsightsService;
import com.edutrack.application.insights.ProgressPoint;
import com.edutrack.application.insights.WeekMinutes;
import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.progress.ConsistencyService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureSeries;
import com.edutrack.domain.progress.Progress;
import com.edutrack.domain.progress.StreakCalculator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InsightsViewModelTest {

    private final AreaService areas = mock(AreaService.class);
    private final GoalService goals = mock(GoalService.class);
    private final InsightsService insights = mock(InsightsService.class);
    private final ConsistencyService consistency = mock(ConsistencyService.class);
    private final MeasureService measures = mock(MeasureService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-28T12:00:00Z"), ZoneOffset.UTC); // a Wednesday
    private final AreaId english = AreaId.newId();
    private final AreaId guitar = AreaId.newId();
    private final GoalId goalId = GoalId.newId();
    private Preferences node;
    private InsightsViewModel viewModel;

    @BeforeEach
    void setUp() {
        node = Preferences.userRoot().node("edutrack-insights-test-" + UUID.randomUUID());
        when(areas.listAreas()).thenReturn(List.of(
                new AreaSummary(english, "English", null, AreaIcon.GLOBE, 1, Progress.ZERO),
                new AreaSummary(guitar, "Guitar", null, AreaIcon.MUSIC, 0, Progress.ZERO)));
        when(goals.listGoals(english)).thenReturn(List.of(
                new GoalSummary(goalId, english, "IELTS", null, Progress.ZERO, 3, 0, false)));
        when(goals.listGoals(guitar)).thenReturn(List.of());
        when(insights.weeklyMinutes(eq(12), eq(DayOfWeek.MONDAY))).thenReturn(List.of(
                new WeekMinutes(LocalDate.of(2026, 10, 19), Map.of(english, 180, guitar, 60), 240),
                new WeekMinutes(LocalDate.of(2026, 10, 26), Map.of(english, 120), 120)));
        when(insights.dailyMinutes(any(), any())).thenReturn(Map.of(LocalDate.of(2026, 10, 27), 45));
        when(insights.goalProgressHistory(goalId)).thenReturn(List.of(
                new ProgressPoint(LocalDate.of(2026, 9, 1), Progress.ZERO),
                new ProgressPoint(LocalDate.of(2026, 10, 10), new Progress(new BigDecimal("40")))));
        when(consistency.streak()).thenReturn(new StreakCalculator.Streak(3, 9, true));
        when(consistency.daysThisWeek(any())).thenReturn(2);
        when(measures.forGoal(goalId)).thenReturn(List.of());
        viewModel = new InsightsViewModel(areas, goals, insights, consistency, measures, new UiPreferences(node),
                dataChanges, clock, new Messages());
    }

    @AfterEach
    void tearDown() throws BackingStoreException {
        node.removeNode();
    }

    @Test
    void weeklyBarsConvertMinutesToHoursAndMarkTheCurrentWeek() {
        assertThat(viewModel.weekBars()).extracting(InsightsViewModel.WeekBar::hours).containsExactly(4.0, 2.0);
        assertThat(viewModel.weekBars()).extracting(InsightsViewModel.WeekBar::current).containsExactly(false, true);
        assertThat(viewModel.weekBars().getFirst().tooltip()).isEqualTo("Week of 19 Oct · 4 h");
        assertThat(viewModel.weekSummaryProperty().get()).isEqualTo("6 h in the last 2 weeks · 3 h/week on average");
    }

    @Test
    void filteringByAreaCountsOnlyThatAreasTime() {
        viewModel.areaFilterProperty().set(viewModel.areaChoices().stream()
                .filter(choice -> english.equals(choice.id())).findFirst().orElseThrow());

        assertThat(viewModel.weekBars()).extracting(InsightsViewModel.WeekBar::hours).containsExactly(3.0, 2.0);
    }

    @Test
    void anEmptyPeriodSaysNothingWasLogged() {
        when(insights.weeklyMinutes(eq(26), eq(DayOfWeek.MONDAY))).thenReturn(List.of(
                new WeekMinutes(LocalDate.of(2026, 10, 26), Map.of(), 0)));

        viewModel.rangeProperty().set(viewModel.ranges().get(1));

        assertThat(viewModel.weekSummaryProperty().get()).isEqualTo("Nothing logged in this period yet.");
    }

    @Test
    void theHeatmapStartsOnAWeekStartAndCoversTwentySixWeeks() {
        InsightsViewModel.Heat heat = viewModel.heatProperty().get();

        assertThat(heat.firstDay().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(heat.firstDay()).isEqualTo(LocalDate.of(2026, 10, 26).minusWeeks(25));
        assertThat(heat.minutes()).containsEntry(LocalDate.of(2026, 10, 27), 45);
        assertThat(viewModel.streakTextProperty().get())
                .isEqualTo("Current streak 3 days · longest 9 · 2 study days this week");
    }

    @Test
    void theHeatLevelsFollowTheMinutesStudied() {
        assertThat(new int[]{Heatmap.level(0), Heatmap.level(10), Heatmap.level(30), Heatmap.level(59),
                Heatmap.level(60), Heatmap.level(119), Heatmap.level(120), Heatmap.level(600)})
                .containsExactly(0, 1, 2, 2, 3, 3, 4, 4);
    }

    @Test
    void theGoalBoxListsEveryGoalAndChartsItsProgress() {
        assertThat(viewModel.goalChoices()).extracting(InsightsViewModel.GoalChoice::label).containsExactly("English · IELTS");
        assertThat(viewModel.progressPoints()).extracting(InsightsViewModel.LinePoint::value).containsExactly(0.0, 40.0);
    }

    @Test
    void measuresBecomeLinesWithTheirTargetAndDirection() {
        MeasureView band = new MeasureView(MeasureId.newId(), goalId, "Overall band", "band", new BigDecimal("9"),
                new BigDecimal("7"), Direction.HIGHER_IS_BETTER, CombineRule.AVERAGE, null, 0,
                List.of(new MeasureSeries.Point(LocalDate.of(2026, 10, 1), new BigDecimal("6.0"), 4),
                        new MeasureSeries.Point(LocalDate.of(2026, 10, 20), new BigDecimal("6.5"), 4)),
                null, null, false, 8);
        when(measures.forGoal(goalId)).thenReturn(List.of(band));

        dataChanges.markChanged();

        assertThat(viewModel.measureLines()).hasSize(1);
        InsightsViewModel.MeasureLine line = viewModel.measureLines().getFirst();
        assertThat(line.target()).isEqualTo(7.0);
        assertThat(line.points()).extracting(InsightsViewModel.LinePoint::value).containsExactly(6.0, 6.5);
        assertThat(line.caption()).startsWith("Higher is better");
    }

    @Test
    void nothingIsChartedWithoutAreas() {
        when(areas.listAreas()).thenReturn(List.of());

        viewModel.refresh();

        assertThat(viewModel.emptyProperty().get()).isTrue();
        assertThat(viewModel.goalChoices()).isEmpty();
        assertThat(viewModel.progressPoints()).isEmpty();
    }
}
