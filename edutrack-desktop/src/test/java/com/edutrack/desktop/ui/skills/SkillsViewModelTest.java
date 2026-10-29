package com.edutrack.desktop.ui.skills;

import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.application.skill.CompetencyView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureId;
import com.edutrack.domain.measure.MeasureSeries;
import com.edutrack.domain.skill.CompetencyId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SkillsViewModelTest {

    private final CompetencyService competencies = mock(CompetencyService.class);
    private final MeasureService measures = mock(MeasureService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final SkillsViewModel viewModel = new SkillsViewModel(competencies, measures, dataChanges, new Messages());
    private final GoalId goalId = GoalId.newId();

    private static MeasureSeries.Point point(int day, String value) {
        return new MeasureSeries.Point(LocalDate.of(2026, 10, day), new BigDecimal(value), 1);
    }

    private MeasureView measure(Direction direction, BigDecimal max, BigDecimal target, List<MeasureSeries.Point> points,
                                boolean met) {
        return new MeasureView(MeasureId.newId(), goalId, "Overall band", "band", max, target, direction,
                CombineRule.AVERAGE, null, 0, points, points.isEmpty() ? null : points.getLast(),
                points.stream().max((a, b) -> a.value().compareTo(b.value())).orElse(null), met, points.size());
    }

    @Test
    void levelLabelsCoverZeroToFiveAndClampOutsideIt() {
        assertThat(viewModel.levelLabel(0)).isEqualTo("Not started");
        assertThat(viewModel.levelLabel(3)).isEqualTo("Practising");
        assertThat(viewModel.levelLabel(5)).isEqualTo("Mastered");
        assertThat(viewModel.levelLabel(9)).isEqualTo("Mastered");
        assertThat(viewModel.levelLabel(-2)).isEqualTo("Not started");
    }

    @Test
    void aMeasureShowsItsLatestValueAgainstTheMaximumAndTheTarget() {
        MeasureView band = measure(Direction.HIGHER_IS_BETTER, new BigDecimal("9"), new BigDecimal("7"),
                List.of(point(1, "6.0"), point(10, "6.5"), point(20, "7.0")), true);

        SkillsViewModel.MeasureRow row = viewModel.row(band);

        assertThat(row.latestText()).isEqualTo("7 / 9 band");
        assertThat(row.targetText()).isEqualTo("target 7 band");
        assertThat(row.bestText()).isEqualTo("best 7 band");
        assertThat(row.resultsText()).isEqualTo("3 results");
    }

    @Test
    void aMeasureWithoutResultsSaysSo() {
        SkillsViewModel.MeasureRow row = viewModel.row(measure(Direction.HIGHER_IS_BETTER, null, null, List.of(), false));

        assertThat(row.latestText()).isEqualTo("no results yet");
        assertThat(row.targetText()).isEmpty();
        assertThat(row.bestText()).isEmpty();
        assertThat(row.trend()).isEmpty();
    }

    @Test
    void lowerIsBetterMeasuresAskForAtMostTheTarget() {
        MeasureView errors = measure(Direction.LOWER_IS_BETTER, null, new BigDecimal("2"),
                List.of(point(1, "8"), point(2, "5"), point(3, "3")), false);

        assertThat(viewModel.row(errors).targetText()).isEqualTo("target 2 band or less");
    }

    @Test
    void theTrendScalesToTheRangeAndFlipsWhenLowerIsBetter() {
        List<MeasureSeries.Point> points = List.of(point(1, "5"), point(2, "7.5"), point(3, "10"));

        assertThat(SkillsViewModel.trend(measure(Direction.HIGHER_IS_BETTER, null, null, points, false)))
                .containsExactly(0.0, 0.5, 1.0);
        assertThat(SkillsViewModel.trend(measure(Direction.LOWER_IS_BETTER, null, null, points, false)))
                .containsExactly(1.0, 0.5, 0.0);
        assertThat(SkillsViewModel.trend(measure(Direction.HIGHER_IS_BETTER, null, null, List.of(point(1, "5")), false)))
                .isEmpty();
        assertThat(SkillsViewModel.trend(measure(Direction.HIGHER_IS_BETTER, null, null,
                List.of(point(1, "5"), point(2, "5")), false))).containsExactly(0.5, 0.5);
    }

    @Test
    void actionsGoThroughTheServicesAndAnnounceTheChange() {
        CompetencyId competency = CompetencyId.newId();
        MeasureId measure = MeasureId.newId();
        long[] changes = {0};
        dataChanges.subscribe(() -> changes[0]++);

        viewModel.setLevel(competency, 4);
        viewModel.moveCompetency(competency, MoveDirection.UP);
        viewModel.deleteCompetency(competency);
        viewModel.deleteMeasure(measure);

        verify(competencies).changeLevel(competency, 4);
        verify(competencies).move(competency, MoveDirection.UP);
        verify(competencies).delete(competency);
        verify(measures).delete(measure);
        assertThat(changes[0]).isEqualTo(4);
    }

    @Test
    void competencyRowsWordTheLevelTargetAndLinkedSteps() {
        CompetencyView listening = new CompetencyView(CompetencyId.newId(), goalId, "Listening", null, 3, 5, false, 0,
                List.of(), List.of());

        SkillsViewModel.CompetencyRow row = viewModel.row(listening);

        assertThat(row.levelText()).isEqualTo("Practising");
        assertThat(row.targetText()).isEqualTo("target: Mastered");
        assertThat(row.stepsText()).isEqualTo("no steps linked");
    }

    @Test
    void theMeasureDialogOnlyAcceptsNumbersForMaximumAndTarget() {
        CompetencyService service = mock(CompetencyService.class);
        MeasureDialogViewModel dialog = new MeasureDialogViewModel(measures, service, new Messages());
        dialog.create(goalId);

        dialog.nameProperty().set("Band");
        assertThat(dialog.validProperty().get()).isTrue();
        dialog.maxValueProperty().set("9");
        dialog.targetProperty().set("7,5");
        assertThat(dialog.validProperty().get()).isTrue();
        dialog.targetProperty().set("seven");
        assertThat(dialog.validProperty().get()).isFalse();
        dialog.targetProperty().set("");
        dialog.nameProperty().set(" ");
        assertThat(dialog.validProperty().get()).isFalse();
    }
}
