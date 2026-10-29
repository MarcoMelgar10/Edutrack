package com.edutrack.desktop.ui.area;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.progress.Progress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class AreaTabViewModelTest {

    private final AreaService areaService = mock(AreaService.class);
    private final GoalService goalService = mock(GoalService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final AreaTabViewModel viewModel = new AreaTabViewModel(areaService, goalService, dataChanges);

    private final AreaId areaId = AreaId.newId();
    private final GoalSummary ielts = goal("IELTS 7.0");
    private final GoalSummary cambridge = goal("Cambridge C1");

    private GoalSummary goal(String title) {
        return new GoalSummary(GoalId.newId(), areaId, title, LocalDate.of(2027, 3, 1),
                new Progress(new BigDecimal("26.32")), 4, 1, false);
    }

    private AreaSummary english(String progress) {
        return new AreaSummary(areaId, "English", "", AreaIcon.GLOBE, 2, new Progress(new BigDecimal(progress)));
    }

    private void areaHasGoals(GoalSummary... goals) {
        when(areaService.getArea(areaId)).thenReturn(english("45"));
        when(goalService.listGoals(areaId)).thenReturn(List.of(goals));
    }

    @Test
    void showLoadsTheHeaderAndGoalsAndSelectsTheFirstGoal() {
        areaHasGoals(ielts, cambridge);

        viewModel.show(areaId);

        assertThat(viewModel.areaId()).isEqualTo(areaId);
        assertThat(viewModel.headerTitleProperty().get()).isEqualTo("English");
        assertThat(viewModel.headerProgressProperty().get()).isEqualTo("45%");
        assertThat(viewModel.areaProperty().get().name()).isEqualTo("English");
        assertThat(viewModel.goals()).containsExactly(ielts, cambridge);
        assertThat(viewModel.selectedGoalProperty().get()).isEqualTo(ielts.id());
    }

    @Test
    void refreshKeepsAnExistingSelection() {
        areaHasGoals(ielts, cambridge);
        viewModel.show(areaId);
        viewModel.select(cambridge.id());

        dataChanges.markChanged();

        assertThat(viewModel.selectedGoalProperty().get()).isEqualTo(cambridge.id());
        assertThat(viewModel.goals()).containsExactly(ielts, cambridge);
    }

    @Test
    void refreshFollowsChangedNumbers() {
        areaHasGoals(ielts);
        viewModel.show(areaId);
        when(areaService.getArea(areaId)).thenReturn(english("60"));

        dataChanges.markChanged();

        assertThat(viewModel.headerProgressProperty().get()).isEqualTo("60%");
    }

    @Test
    void refreshFallsBackToTheFirstGoalWhenTheSelectedOneDisappears() {
        areaHasGoals(ielts, cambridge);
        viewModel.show(areaId);
        viewModel.select(cambridge.id());
        when(goalService.listGoals(areaId)).thenReturn(List.of(ielts));

        dataChanges.markChanged();

        assertThat(viewModel.goals()).containsExactly(ielts);
        assertThat(viewModel.selectedGoalProperty().get()).isEqualTo(ielts.id());
    }

    @Test
    void selectionIsNullWhenTheAreaHasNoGoals() {
        areaHasGoals();

        viewModel.show(areaId);

        assertThat(viewModel.goals()).isEmpty();
        assertThat(viewModel.selectedGoalProperty().get()).isNull();
    }

    @Test
    void selectionBecomesNullWhenTheLastGoalIsDeleted() {
        areaHasGoals(ielts);
        viewModel.show(areaId);
        when(goalService.listGoals(areaId)).thenReturn(List.of());

        dataChanges.markChanged();

        assertThat(viewModel.selectedGoalProperty().get()).isNull();
    }

    @Test
    void aNewGoalIsSelectedOnceItAppears() {
        areaHasGoals(ielts);
        viewModel.show(areaId);
        when(goalService.listGoals(areaId)).thenReturn(List.of(ielts, cambridge));
        dataChanges.markChanged();

        viewModel.select(cambridge.id());

        assertThat(viewModel.selectedGoalProperty().get()).isEqualTo(cambridge.id());
    }

    @Test
    void anAreaDeletedMeanwhileIsIgnoredDuringRefresh() {
        areaHasGoals(ielts, cambridge);
        viewModel.show(areaId);
        when(areaService.getArea(areaId)).thenThrow(new NotFoundException("Area", areaId));
        when(goalService.listGoals(areaId)).thenReturn(List.of());

        dataChanges.markChanged();

        assertThat(viewModel.headerTitleProperty().get()).isEqualTo("English");
        assertThat(viewModel.goals()).containsExactly(ielts, cambridge);
        assertThat(viewModel.selectedGoalProperty().get()).isEqualTo(ielts.id());
    }

    @Test
    void followsDataChangesUntilItIsDisposed() {
        areaHasGoals(ielts);
        viewModel.show(areaId);
        verify(goalService, times(1)).listGoals(areaId);

        dataChanges.markChanged();
        verify(goalService, times(2)).listGoals(areaId);

        viewModel.dispose();
        clearInvocations(areaService, goalService);
        dataChanges.markChanged();

        verifyNoMoreInteractions(areaService, goalService);
    }

    @Test
    void movingAGoalAsksTheServiceAndKeepsItSelected() {
        areaHasGoals(ielts, cambridge);
        viewModel.show(areaId);
        viewModel.select(cambridge.id());
        when(goalService.listGoals(areaId)).thenReturn(List.of(cambridge, ielts));

        viewModel.moveGoal(cambridge.id(), MoveDirection.UP);

        verify(goalService).moveGoal(cambridge.id(), MoveDirection.UP);
        assertThat(viewModel.goals()).containsExactly(cambridge, ielts);
        assertThat(viewModel.selectedGoalProperty().get()).isEqualTo(cambridge.id());
    }

    @Test
    void draggingAGoalSwapsItOnePlaceAtATimeAndReloadsOnce() {
        GoalSummary third = goal("TOEFL");
        areaHasGoals(ielts, cambridge, third);
        viewModel.show(areaId);
        clearInvocations(goalService);
        when(goalService.listGoals(areaId)).thenReturn(List.of(cambridge, third, ielts));

        viewModel.moveGoalTo(ielts.id(), 2);

        verify(goalService, times(2)).moveGoal(ielts.id(), MoveDirection.DOWN);
        verify(goalService, times(1)).listGoals(areaId);
        assertThat(viewModel.goals()).containsExactly(cambridge, third, ielts);
    }

    @Test
    void droppingAGoalWhereItIsChangesNothing() {
        areaHasGoals(ielts, cambridge);
        viewModel.show(areaId);

        viewModel.moveGoalTo(ielts.id(), 0);
        viewModel.moveGoalTo(GoalId.newId(), 1);

        verify(goalService, never()).moveGoal(any(), any());
    }

    @Test
    void deleteAreaDeletesItAndSignalsTheChange() {
        areaHasGoals(ielts);
        viewModel.show(areaId);
        AtomicInteger signals = new AtomicInteger();
        dataChanges.subscribe(signals::incrementAndGet);
        when(areaService.getArea(areaId)).thenThrow(new NotFoundException("Area", areaId));

        viewModel.deleteArea();

        verify(areaService).deleteArea(areaId);
        assertThat(signals).hasValue(1);
    }
}
