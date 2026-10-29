package com.edutrack.desktop.ui.goal;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.RecordOwner;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The goal panel on the right of an area tab: header, deadline, progress and the roadmap.
 *
 * <p>Every action calls the {@link GoalService}, renders the returned {@link GoalDetails} and
 * signals {@link DataChanges} so cards, tab headers and the Overview follow. The goal's own
 * results (final exams, certificates) come from its learning record.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class GoalDetailViewModel {

    private final GoalService goalService;
    private final LearningRecordService recordService;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ReadOnlyObjectWrapper<GoalDetails> goal = new ReadOnlyObjectWrapper<>();
    private final ObservableList<StepView> steps = FXCollections.observableArrayList();
    private final ObservableList<StepView> stepsReadOnly = FXCollections.unmodifiableObservableList(steps);
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper description = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper deadlineText = new ReadOnlyStringWrapper("");
    private final ReadOnlyDoubleWrapper progress = new ReadOnlyDoubleWrapper(0);
    private final ReadOnlyStringWrapper progressText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper effortText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper completed = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper completionText = new ReadOnlyStringWrapper("");
    private final ReadOnlyBooleanWrapper hasGoal = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyBooleanWrapper hasSteps = new ReadOnlyBooleanWrapper(false);
    private final ObservableList<RecordView.ResultView> results = FXCollections.observableArrayList();
    private final ObservableList<RecordView.ResultView> resultsReadOnly = FXCollections.unmodifiableObservableList(results);
    private GoalId resultsLoadedFor;

    public GoalDetailViewModel(GoalService goalService, LearningRecordService recordService, DataChanges dataChanges,
                               Clock clock, Messages messages) {
        this.goalService = goalService;
        this.recordService = recordService;
        this.dataChanges = dataChanges;
        this.clock = clock;
        this.messages = messages;
        goal.subscribe(this::render);
    }

    /** Loads a goal, or clears the panel for {@code null} (area without goals). */
    public void show(GoalId goalId) {
        if (goalId == null) {
            goal.set(null);
            return;
        }
        if (goal.get() != null && goal.get().id().equals(goalId)) {
            return;
        }
        try {
            goal.set(goalService.getGoal(goalId));
        } catch (NotFoundException deleted) {
            goal.set(null);
        }
    }

    /** Loads the shown goal again, e.g. after its step pages changed the record summaries. */
    public void reload() {
        if (goal.get() == null) {
            return;
        }
        try {
            goal.set(goalService.getGoal(goal.get().id()));
        } catch (NotFoundException deleted) {
            goal.set(null);
        }
    }

    /** Shows a result returned by a dialog (edit goal, add/edit step). */
    public void apply(GoalDetails details) {
        goal.set(details);
    }

    public void cycleStatus(StepId stepId) {
        change(() -> goalService.cycleStepStatus(currentId(), stepId));
    }

    public void moveStep(StepId stepId, MoveDirection direction) {
        change(() -> goalService.moveStep(currentId(), stepId, direction));
    }

    public void removeStep(StepId stepId) {
        change(() -> goalService.removeStep(currentId(), stepId));
    }

    public void deleteGoal() {
        goalService.deleteGoal(currentId());
        goal.set(null);
        dataChanges.markChanged();
    }

    /** The record holding this goal's results; the Result dialog saves into it. */
    public RecordOwner resultsOwner() {
        return RecordOwner.ofGoal(currentId());
    }

    /** Shows the results returned by the Result dialog. */
    public void applyResults(RecordView record) {
        results.setAll(record.results());
    }

    public void removeResult(AssessmentId resultId) {
        applyResults(recordService.removeResult(resultsOwner(), resultId));
    }

    private void change(Supplier<GoalDetails> action) {
        goal.set(action.get());
        dataChanges.markChanged();
    }

    private GoalId currentId() {
        return Objects.requireNonNull(goal.get(), "No goal is shown").id();
    }

    private void render(GoalDetails details) {
        hasGoal.set(details != null);
        if (details == null) {
            steps.clear();
            results.clear();
            resultsLoadedFor = null;
            hasSteps.set(false);
            completed.set(false);
            return;
        }
        steps.setAll(details.steps());
        if (!details.id().equals(resultsLoadedFor)) {
            results.setAll(recordService.open(RecordOwner.ofGoal(details.id())).results());
            resultsLoadedFor = details.id();
        }
        hasSteps.set(!details.steps().isEmpty());
        title.set(details.title());
        description.set(details.description() == null ? "" : details.description());
        deadlineText.set(Formats.deadline(details.deadline(), LocalDate.now(clock)));
        progress.set(details.progress().fraction());
        progressText.set(Formats.percent(details.progress()));
        int doneSteps = (int) details.steps().stream().filter(step -> step.status() == StepStatus.DONE).count();
        effortText.set(Formats.effort(details.doneHours(), details.totalHours(), details.unestimatedSteps(),
                doneSteps, details.steps().size()));
        completionText.set(completionText(details));
        completed.set(details.completed());
    }

    /** "Goal completed · 27 Oct 2026", dated by the last step that was marked Done. */
    private String completionText(GoalDetails details) {
        Instant finished = details.steps().stream()
                .map(StepView::completedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return finished == null ? messages.get("goal.completed")
                : messages.get("goal.completedOn", Formats.date(finished, clock.getZone()));
    }

    public ReadOnlyObjectProperty<GoalDetails> goalProperty() {
        return goal.getReadOnlyProperty();
    }

    public ObservableList<StepView> steps() {
        return stepsReadOnly; // one shared wrapper: it only weakly listens to the source list
    }

    /** The goal's own results, newest first. */
    public ObservableList<RecordView.ResultView> results() {
        return resultsReadOnly; // one shared wrapper: it only weakly listens to the source list
    }

    public ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty descriptionProperty() {
        return description.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty deadlineTextProperty() {
        return deadlineText.getReadOnlyProperty();
    }

    /** 0–1, for the progress bar. */
    public ReadOnlyDoubleProperty progressProperty() {
        return progress.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty progressTextProperty() {
        return progressText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty effortTextProperty() {
        return effortText.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty completedProperty() {
        return completed.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty completionTextProperty() {
        return completionText.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty hasGoalProperty() {
        return hasGoal.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty hasStepsProperty() {
        return hasSteps.getReadOnlyProperty();
    }
}
