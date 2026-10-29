package com.edutrack.desktop.ui.step;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.PendingWork;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.goal.StepStatus;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.ExerciseId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.RecordSummary;
import com.edutrack.domain.record.ResourceId;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/**
 * The step page: header (goal › step, status, hours) and the step's learning record in four tabs —
 * Markdown notes, resources, exercises and results.
 *
 * <p>Notes are saved by {@link #saveNotesIfChanged()}, which the view calls after a pause in typing,
 * when the editor loses focus and before the page closes; a save never touches the text being
 * typed. Record changes don't affect progress, so only a status change signals {@link DataChanges}.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StepPageViewModel implements PendingWork.Source {

    public enum SaveState {
        SAVED,
        UNSAVED,
        FAILED
    }

    private final LearningRecordService records;
    private final GoalService goals;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ReadOnlyBooleanWrapper open = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyStringWrapper goalTitle = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper stepTitle = new ReadOnlyStringWrapper("");
    private final ReadOnlyObjectWrapper<StepStatus> status = new ReadOnlyObjectWrapper<>(StepStatus.NOT_STARTED);
    private final ReadOnlyStringWrapper statusText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper hoursText = new ReadOnlyStringWrapper("");
    private String completedOn = "";

    private final StringProperty notes = new SimpleStringProperty("");
    private final ReadOnlyObjectWrapper<SaveState> saveState = new ReadOnlyObjectWrapper<>(SaveState.SAVED);
    private final ReadOnlyStringWrapper saveText = new ReadOnlyStringWrapper("");

    private final ObservableList<RecordView.ResourceView> resources = FXCollections.observableArrayList();
    private final ObservableList<RecordView.ExerciseView> exercises = FXCollections.observableArrayList();
    private final ObservableList<RecordView.ResultView> results = FXCollections.observableArrayList();
    // one shared wrapper per list: they only weakly listen to the source lists
    private final ObservableList<RecordView.ResourceView> resourcesReadOnly = FXCollections.unmodifiableObservableList(resources);
    private final ObservableList<RecordView.ExerciseView> exercisesReadOnly = FXCollections.unmodifiableObservableList(exercises);
    private final ObservableList<RecordView.ResultView> resultsReadOnly = FXCollections.unmodifiableObservableList(results);
    private final ReadOnlyStringWrapper resourcesTabText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper exercisesTabText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper resultsTabText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper exerciseError = new ReadOnlyStringWrapper("");

    private GoalId goalId;
    private StepId stepId;
    private RecordOwner owner;
    private String savedNotes = "";
    private boolean loading;

    public StepPageViewModel(LearningRecordService records, GoalService goals, DataChanges dataChanges,
                             PendingWork pendingWork, Clock clock, Messages messages) {
        this.records = records;
        this.goals = goals;
        this.dataChanges = dataChanges;
        pendingWork.track(this);
        this.clock = clock;
        this.messages = messages;
        resourcesTabText.set(messages.get("step.tab.resources"));
        exercisesTabText.set(messages.get("step.tab.exercises"));
        resultsTabText.set(messages.get("step.tab.results"));
        notes.subscribe(text -> {
            if (!loading && owner != null && !Objects.equals(text, savedNotes)) {
                saveState.set(SaveState.UNSAVED);
                saveText.set(messages.get("step.save.unsaved"));
            }
        });
    }

    /** Shows one step of the goal with its learning record. */
    public void open(GoalDetails goal, StepId stepId) {
        this.goalId = goal.id();
        this.stepId = stepId;
        this.owner = RecordOwner.ofStep(stepId);
        goalTitle.set(goal.title());
        renderStep(goal.step(stepId));

        RecordView record = records.open(owner);
        loading = true;
        try {
            notes.set(record.notes());
        } finally {
            loading = false;
        }
        savedNotes = record.notes();
        saveState.set(SaveState.SAVED);
        saveText.set(record.updatedAt() == null ? "" : messages.get("step.save.saved"));
        exerciseError.set("");
        renderRecord(record);
        open.set(true);
    }

    /**
     * Saves pending notes and hides the page. If the notes could not be saved, the page stays open
     * with the reason shown and this returns {@code false}, so no text is lost by navigating away.
     */
    public boolean close() {
        saveNotesIfChanged();
        if (saveState.get() == SaveState.FAILED) {
            return false;
        }
        open.set(false);
        return true;
    }

    /** On shutdown: notes typed less than a pause ago are saved instead of lost. */
    @Override
    public void flushPending() {
        saveNotesIfChanged();
    }

    public void saveNotesIfChanged() {
        if (owner == null || Objects.equals(notes.get(), savedNotes)) {
            return;
        }
        String text = notes.get();
        try {
            RecordView record = records.saveNotes(owner, text);
            savedNotes = text;
            renderRecord(record);
            boolean typedMeanwhile = !Objects.equals(notes.get(), text);
            saveState.set(typedMeanwhile ? SaveState.UNSAVED : SaveState.SAVED);
            saveText.set(typedMeanwhile ? messages.get("step.save.unsaved")
                    : messages.get("step.save.savedAt", Formats.time(clock.instant(), clock.getZone())));
        } catch (DomainValidationException rejected) {
            saveState.set(SaveState.FAILED);
            saveText.set(messages.error(rejected));
        }
    }

    /** The click on the status mark in the header: Not started → In progress → Done → Not started. */
    public void cycleStatus() {
        GoalDetails goal = goals.cycleStepStatus(goalId, stepId);
        renderStep(goal.step(stepId));
        dataChanges.markChanged();
    }

    /** Shows a record returned by a dialog (resource, exercise, result). */
    public void apply(RecordView record) {
        renderRecord(record);
    }

    public void markResourceReviewed(ResourceId resourceId, boolean reviewed) {
        apply(records.markResourceReviewed(owner, resourceId, reviewed));
    }

    public void removeResource(ResourceId resourceId) {
        apply(records.removeResource(owner, resourceId));
    }

    /** Adds an exercise typed in the inline field; returns false (and sets the error) when rejected. */
    public boolean addExercise(String title) {
        if (title == null || title.isBlank()) {
            return false;
        }
        try {
            apply(records.addExercise(owner, title));
            exerciseError.set("");
            return true;
        } catch (DomainValidationException rejected) {
            exerciseError.set(messages.error(rejected));
            return false;
        }
    }

    public void markExerciseDone(ExerciseId exerciseId, boolean done) {
        apply(records.markExerciseDone(owner, exerciseId, done));
    }

    public void moveExercise(ExerciseId exerciseId, MoveDirection direction) {
        apply(records.moveExercise(owner, exerciseId, direction));
    }

    public void removeExercise(ExerciseId exerciseId) {
        apply(records.removeExercise(owner, exerciseId));
    }

    public void removeResult(AssessmentId resultId) {
        apply(records.removeResult(owner, resultId));
    }

    private void renderStep(StepView step) {
        stepTitle.set(step.number() + ". " + step.title());
        status.set(step.status());
        statusText.set(Formats.status(step.status()));
        hoursText.set(Formats.hours(step.estimatedHours()));
        completedOn = Formats.date(step.completedAt(), clock.getZone());
    }

    /** True when the next status click would reopen a Done step and clear its completion date. */
    public boolean nextClickReopens() {
        return status.get() == StepStatus.DONE;
    }

    /** The step's completion date, e.g. "28 Oct 2026"; empty unless it is Done. */
    public String completedOn() {
        return completedOn;
    }

    /** Lists and tab titles only — never the notes text, which may be mid-edit. */
    private void renderRecord(RecordView record) {
        resources.setAll(record.resources());
        exercises.setAll(record.exercises());
        results.setAll(record.results());
        RecordSummary summary = record.summary();
        resourcesTabText.set(summary.resourceCount() == 0 ? messages.get("step.tab.resources")
                : messages.get("step.tab.resourcesCount", summary.resourceCount()));
        exercisesTabText.set(summary.exercisesTotal() == 0 ? messages.get("step.tab.exercises")
                : messages.get("step.tab.exercisesCount", summary.exercisesDone(), summary.exercisesTotal()));
        resultsTabText.set(summary.resultCount() == 0 ? messages.get("step.tab.results")
                : messages.get("step.tab.resultsCount", summary.resultCount()));
    }

    /** The record the Resource, Exercise and Result dialogs save into. */
    /** The goal of the step being shown. */
    public GoalId goalId() {
        return goalId;
    }

    public RecordOwner owner() {
        return owner;
    }

    public ReadOnlyBooleanProperty openProperty() {
        return open.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty goalTitleProperty() {
        return goalTitle.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty stepTitleProperty() {
        return stepTitle.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<StepStatus> statusProperty() {
        return status.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty statusTextProperty() {
        return statusText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty hoursTextProperty() {
        return hoursText.getReadOnlyProperty();
    }

    /** The Markdown being edited; bind it bidirectionally to the editor. */
    public StringProperty notesProperty() {
        return notes;
    }

    public ReadOnlyObjectProperty<SaveState> saveStateProperty() {
        return saveState.getReadOnlyProperty();
    }

    /** "Saved · 14:32", "Unsaved changes", or the reason a save failed. */
    public ReadOnlyStringProperty saveTextProperty() {
        return saveText.getReadOnlyProperty();
    }

    public ObservableList<RecordView.ResourceView> resources() {
        return resourcesReadOnly;
    }

    public ObservableList<RecordView.ExerciseView> exercises() {
        return exercisesReadOnly;
    }

    public ObservableList<RecordView.ResultView> results() {
        return resultsReadOnly;
    }

    public ReadOnlyStringProperty resourcesTabTextProperty() {
        return resourcesTabText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty exercisesTabTextProperty() {
        return exercisesTabText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty resultsTabTextProperty() {
        return resultsTabText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty exerciseErrorProperty() {
        return exerciseError.getReadOnlyProperty();
    }
}
