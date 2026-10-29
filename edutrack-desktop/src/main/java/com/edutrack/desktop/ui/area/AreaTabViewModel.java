package com.edutrack.desktop.ui.area;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One area tab: its header ("English · 45%"), its goal cards and which goal is open on the right.
 * Reloads whenever data changes anywhere, so card percentages follow edits made in the goal panel.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AreaTabViewModel {

    private final AreaService areaService;
    private final GoalService goalService;
    private final DataChanges dataChanges;

    private final ReadOnlyObjectWrapper<AreaSummary> area = new ReadOnlyObjectWrapper<>();
    private final ReadOnlyStringWrapper headerTitle = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper headerProgress = new ReadOnlyStringWrapper("");
    private final ObservableList<GoalSummary> goals = FXCollections.observableArrayList();
    private final ObservableList<GoalSummary> goalsReadOnly = FXCollections.unmodifiableObservableList(goals);
    private final ObjectProperty<GoalId> selectedGoal = new SimpleObjectProperty<>();

    private AreaId areaId;
    private Subscription dataSubscription = Subscription.EMPTY;

    public AreaTabViewModel(AreaService areaService, GoalService goalService, DataChanges dataChanges) {
        this.areaService = areaService;
        this.goalService = goalService;
        this.dataChanges = dataChanges;
    }

    /** Binds this tab to an area, loads it and starts following data changes. Call once. */
    public void show(AreaId areaId) {
        this.areaId = areaId;
        refresh();
        dataSubscription = dataChanges.subscribe(this::refresh);
    }

    /** Stops following data changes; called when the tab is removed. */
    public void dispose() {
        dataSubscription.unsubscribe();
    }

    public void refresh() {
        AreaSummary summary;
        try {
            summary = areaService.getArea(areaId);
        } catch (NotFoundException deleted) {
            return; // the area was deleted; the main window is removing this tab
        }
        area.set(summary);
        headerTitle.set(summary.name());
        headerProgress.set(Formats.percent(summary.progress()));

        List<GoalSummary> loaded = goalService.listGoals(areaId);
        goals.setAll(loaded);
        boolean selectionStillExists = loaded.stream().anyMatch(goal -> goal.id().equals(selectedGoal.get()));
        if (!selectionStillExists) {
            selectedGoal.set(loaded.isEmpty() ? null : loaded.getFirst().id());
        }
    }

    public void select(GoalId goalId) {
        selectedGoal.set(goalId);
    }

    /** Moves a goal card one place up or down; the selection stays on the moved goal. */
    public void moveGoal(GoalId goalId, MoveDirection direction) {
        goalService.moveGoal(goalId, direction);
        dataChanges.markChanged();
    }

    /** Moves a goal card to the position {@code index} (drag and drop), one swap at a time. */
    public void moveGoalTo(GoalId goalId, int index) {
        int from = indexOf(goalId);
        if (from < 0 || index == from) {
            return;
        }
        MoveDirection direction = index < from ? MoveDirection.UP : MoveDirection.DOWN;
        for (int swaps = Math.abs(index - from); swaps > 0; swaps--) {
            goalService.moveGoal(goalId, direction);
        }
        dataChanges.markChanged();
    }

    private int indexOf(GoalId goalId) {
        for (int index = 0; index < goals.size(); index++) {
            if (goals.get(index).id().equals(goalId)) {
                return index;
            }
        }
        return -1;
    }

    public void deleteArea() {
        areaService.deleteArea(areaId);
        dataChanges.markChanged();
    }

    public AreaId areaId() {
        return areaId;
    }

    public ReadOnlyObjectProperty<AreaSummary> areaProperty() {
        return area.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty headerTitleProperty() {
        return headerTitle.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty headerProgressProperty() {
        return headerProgress.getReadOnlyProperty();
    }

    public ObservableList<GoalSummary> goals() {
        return goalsReadOnly; // one shared wrapper: it only weakly listens to the source list
    }

    /** The goal shown in the detail panel; {@code null} when the area has no goals. */
    public ReadOnlyObjectProperty<GoalId> selectedGoalProperty() {
        return selectedGoal;
    }
}
