package com.edutrack.desktop.ui.journal;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.GoalSummary;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.journal.JournalEntryView;
import com.edutrack.application.journal.JournalService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.StudyTime;
import com.edutrack.desktop.ui.settings.UiPreferences;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.journal.EntryDetails;
import com.edutrack.domain.journal.EntryKind;
import com.edutrack.domain.journal.JournalEntryId;
import com.edutrack.domain.journal.JournalFilter;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The Journal page: the filtered entry list, the entry being read and the editor for a note or the weekly
 * check-in. Editing is explicit (Save / Cancel); nothing is stored until {@link #save()} succeeds.
 *
 * <p>The editor's links work as a cascade: choosing a goal also chooses its area, changing the area
 * narrows the goals, and a step can only be one of the chosen goal's steps.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class JournalViewModel {

    public enum KindFilter {
        ALL,
        NOTES,
        CHECK_INS
    }

    private static final int HEADLINE_MAX = 80;

    private final JournalService journal;
    private final AreaService areaService;
    private final GoalService goalService;
    private final UiPreferences preferences;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ObservableList<AreaSummary> areas = FXCollections.observableArrayList();
    private final ObservableList<AreaSummary> areasReadOnly = FXCollections.unmodifiableObservableList(areas);

    // ---- list and filters ----
    private final ObjectProperty<AreaId> areaFilter = new SimpleObjectProperty<>();
    private final ObjectProperty<GoalId> goalFilter = new SimpleObjectProperty<>();
    private final ObjectProperty<KindFilter> kindFilter = new SimpleObjectProperty<>(KindFilter.ALL);
    private final ObservableList<GoalSummary> filterGoals = FXCollections.observableArrayList();
    private final ObservableList<GoalSummary> filterGoalsReadOnly = FXCollections.unmodifiableObservableList(filterGoals);
    private final ObservableList<JournalEntryView> entries = FXCollections.observableArrayList();
    private final ObservableList<JournalEntryView> entriesReadOnly = FXCollections.unmodifiableObservableList(entries);
    private final ObjectProperty<JournalEntryView> selected = new SimpleObjectProperty<>();

    // ---- editor ----
    private final ReadOnlyBooleanWrapper editing = new ReadOnlyBooleanWrapper(false);
    private final ReadOnlyObjectWrapper<EntryKind> editingKind = new ReadOnlyObjectWrapper<>(EntryKind.NOTE);
    private final ObjectProperty<LocalDate> editDate = new SimpleObjectProperty<>();
    private final StringProperty editTitle = new SimpleStringProperty("");
    private final StringProperty editBody = new SimpleStringProperty("");
    private final ObjectProperty<Integer> editConfidence = new SimpleObjectProperty<>();
    private final ObjectProperty<AreaId> editArea = new SimpleObjectProperty<>();
    private final ObjectProperty<GoalId> editGoal = new SimpleObjectProperty<>();
    private final ObjectProperty<StepId> editStep = new SimpleObjectProperty<>();
    private final ObservableList<GoalSummary> editGoals = FXCollections.observableArrayList();
    private final ObservableList<GoalSummary> editGoalsReadOnly = FXCollections.unmodifiableObservableList(editGoals);
    private final ObservableList<StepView> editSteps = FXCollections.observableArrayList();
    private final ObservableList<StepView> editStepsReadOnly = FXCollections.unmodifiableObservableList(editSteps);
    private final ReadOnlyStringWrapper editorTitle = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper weekText = new ReadOnlyStringWrapper("");
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper("");
    private final BooleanProperty dirty = new SimpleBooleanProperty(false);
    private final BooleanBinding valid;
    private JournalEntryId editingId;
    private boolean loading;
    private final Subscription dataSubscription;

    public JournalViewModel(JournalService journal, AreaService areaService, GoalService goalService,
                            UiPreferences preferences, DataChanges dataChanges, Clock clock, Messages messages) {
        this.journal = journal;
        this.areaService = areaService;
        this.goalService = goalService;
        this.preferences = preferences;
        this.dataChanges = dataChanges;
        this.clock = clock;
        this.messages = messages;

        valid = Bindings.createBooleanBinding(() -> editDate.get() != null && (editingKind.get() == EntryKind.CHECK_IN
                        ? editConfidence.get() != null : !editBody.get().isBlank()),
                editDate, editingKind, editConfidence, editBody);

        areaFilter.subscribe((before, after) -> {
            rebuildFilterGoals();
            if (goalFilter.get() != null && filterGoals.stream().noneMatch(goal -> goal.id().equals(goalFilter.get()))) {
                goalFilter.set(null);
            }
            refreshEntries();
        });
        goalFilter.subscribe((before, after) -> refreshEntries());
        kindFilter.subscribe((before, after) -> refreshEntries());

        editArea.subscribe((before, after) -> {
            if (loading) {
                return;
            }
            editGoals.setAll(goalsOf(after));
            if (editGoal.get() != null && editGoals.stream().noneMatch(goal -> goal.id().equals(editGoal.get()))) {
                editGoal.set(null);
            }
        });
        editGoal.subscribe((before, after) -> {
            if (loading) {
                return;
            }
            editSteps.setAll(stepsOf(after));
            if (editStep.get() != null && editSteps.stream().noneMatch(step -> step.id().equals(editStep.get()))) {
                editStep.set(null);
            }
            if (after != null) {
                goalSummary(after).map(GoalSummary::areaId).filter(area -> !area.equals(editArea.get()))
                        .ifPresent(editArea::set);
            }
            updateWeekText();
        });
        editDate.subscribe(this::updateWeekText);
        for (javafx.beans.value.ObservableValue<?> property : List.of(editDate, editTitle, editBody, editConfidence,
                editArea, editGoal, editStep)) {
            property.subscribe(() -> {
                if (!loading) {
                    dirty.set(true);
                }
            });
        }

        reloadAreas();
        rebuildFilterGoals();
        refreshEntries();
        dataSubscription = dataChanges.subscribe(this::onDataChanged);
    }

    /** Stops following data changes; called when the shell holding the page is replaced. */
    public void dispose() {
        dataSubscription.unsubscribe();
    }

    private void onDataChanged() {
        reloadAreas();
        rebuildFilterGoals();
        refreshEntries();
    }

    /** Reads everything again, e.g. when the page is brought to the front. */
    public void refresh() {
        onDataChanged();
    }

    // ---- list ----

    private void reloadAreas() {
        areas.setAll(areaService.listAreas());
    }

    private void rebuildFilterGoals() {
        filterGoals.setAll(goalsOf(areaFilter.get()));
    }

    private void refreshEntries() {
        JournalEntryView before = selected.get();
        entries.setAll(journal.list(new JournalFilter(areaFilter.get(), goalFilter.get(), switch (kindFilter.get()) {
            case ALL -> null;
            case NOTES -> EntryKind.NOTE;
            case CHECK_INS -> EntryKind.CHECK_IN;
        })));
        JournalEntryView kept = before == null ? null : entries.stream()
                .filter(entry -> entry.id().equals(before.id())).findFirst().orElse(null);
        if (editing.get()) {
            selected.set(kept); // the list changed under the editor; keep what is being written
        } else {
            selected.set(kept != null ? kept : entries.isEmpty() ? null : entries.getFirst());
        }
    }

    /** The entry shown in the read view; {@code null} when the list is empty. */
    public ObjectProperty<JournalEntryView> selectedProperty() {
        return selected;
    }

    public ObservableList<JournalEntryView> entries() {
        return entriesReadOnly;
    }

    public ObservableList<AreaSummary> areas() {
        return areasReadOnly;
    }

    /** The goals the goal filter offers: those of the chosen area, or every goal. */
    public ObservableList<GoalSummary> filterGoals() {
        return filterGoalsReadOnly;
    }

    public ObjectProperty<AreaId> areaFilterProperty() {
        return areaFilter;
    }

    public ObjectProperty<GoalId> goalFilterProperty() {
        return goalFilter;
    }

    public ObjectProperty<KindFilter> kindFilterProperty() {
        return kindFilter;
    }

    // ---- how an entry reads in the list ----

    /** The title, else the first line of the body without Markdown markers, else the name of the kind. */
    public String headline(JournalEntryView entry) {
        if (entry.title() != null && !entry.title().isBlank()) {
            return entry.title();
        }
        String body = entry.body() == null ? "" : entry.body();
        for (String line : body.split("\\R")) {
            String text = line.strip().replaceFirst("^(#{1,6}|[-*+>]|\\d+\\.)\\s+", "").strip();
            if (!text.isEmpty()) {
                return text.length() > HEADLINE_MAX ? text.substring(0, HEADLINE_MAX - 1).stripTrailing() + "…" : text;
            }
        }
        return messages.get(entry.kind() == EntryKind.CHECK_IN ? "journal.untitled.checkIn" : "journal.untitled.note");
    }

    /** The title of the entry as its page shows it: the title, else the name of the kind. */
    public String title(JournalEntryView entry) {
        if (entry.title() != null && !entry.title().isBlank()) {
            return entry.title();
        }
        return messages.get(entry.kind() == EntryKind.CHECK_IN ? "journal.untitled.checkIn" : "journal.untitled.note");
    }

    /** "Goal › Step", the goal, or the area name; empty when the entry is not linked to anything. */
    public String caption(JournalEntryView entry) {
        if (entry.goalTitle() != null) {
            return entry.stepTitle() == null ? entry.goalTitle() : entry.goalTitle() + " › " + entry.stepTitle();
        }
        if (entry.areaId() == null) {
            return "";
        }
        return areas.stream().filter(area -> area.id().equals(entry.areaId())).map(AreaSummary::name)
                .findFirst().orElse("");
    }

    /** "6.5 h" studied in the check-in's week, or empty for a note. */
    public String weekHours(JournalEntryView entry) {
        return entry.weekMinutes() == null ? "" : StudyTime.hoursText(entry.weekMinutes());
    }

    // ---- editor: start and finish ----

    /**
     * A new note dated today, optionally about {@code goalId}. An editor with unsaved changes is never
     * replaced: asking again (e.g. from a link elsewhere in the app) leaves it as it is.
     */
    public void startNote(GoalId goalId) {
        if (!isDirty()) {
            begin(EntryKind.NOTE, null, LocalDate.now(clock), "", "", null, null, goalId, null);
        }
    }

    /** A new weekly check-in dated today, with the reflection template filled in. */
    public void startCheckIn() {
        if (!isDirty()) {
            begin(EntryKind.CHECK_IN, null, LocalDate.now(clock), "", messages.get("journal.checkin.template"), null,
                    null, null, null);
        }
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /** Shows a problem found by the view itself (e.g. a date that could not be read) in the editor. */
    public void fail(DomainValidationException problem) {
        error.set(messages.error(problem));
    }

    /** Edits the selected entry. */
    public void edit() {
        JournalEntryView entry = selected.get();
        if (entry != null) {
            begin(entry.kind(), entry.id(), entry.writtenOn(), entry.title() == null ? "" : entry.title(),
                    entry.body() == null ? "" : entry.body(), entry.confidence(), entry.areaId(), entry.goalId(),
                    entry.stepId());
        }
    }

    /** Leaves the editor without saving. */
    public void cancel() {
        editing.set(false);
        error.set("");
        dirty.set(false);
    }

    private void begin(EntryKind kind, JournalEntryId id, LocalDate date, String title, String body,
                       Integer confidence, AreaId area, GoalId goal, StepId step) {
        loading = true;
        try {
            editingId = id;
            editingKind.set(kind);
            editDate.set(date);
            editTitle.set(title);
            editBody.set(body);
            editConfidence.set(confidence);
            AreaId linkedArea = area != null ? area : goal == null ? null
                    : goalSummary(goal).map(GoalSummary::areaId).orElse(null);
            editArea.set(linkedArea);
            editGoals.setAll(goalsOf(linkedArea));
            editGoal.set(goal);
            editSteps.setAll(stepsOf(goal));
            editStep.set(step);
        } finally {
            loading = false;
        }
        editorTitle.set(messages.get(kind == EntryKind.CHECK_IN
                ? (id == null ? "journal.editor.newCheckIn" : "journal.editor.editCheckIn")
                : (id == null ? "journal.editor.newNote" : "journal.editor.editNote")));
        error.set("");
        dirty.set(false);
        updateWeekText();
        editing.set(true);
    }

    /**
     * Saves the entry being edited. On a rejected value the editor stays open with the reason in
     * {@link #errorProperty()} and this returns {@code false}.
     */
    public boolean save() {
        EntryDetails details = new EntryDetails(editDate.get(), editTitle.get(), editBody.get(), editArea.get(),
                editGoal.get(), editStep.get(), editingKind.get() == EntryKind.CHECK_IN ? editConfidence.get() : null);
        try {
            JournalEntryView saved;
            if (editingId != null) {
                saved = journal.update(editingId, details, preferences.getWeekStart());
            } else if (editingKind.get() == EntryKind.CHECK_IN) {
                saved = journal.checkIn(details, preferences.getWeekStart());
            } else {
                saved = journal.writeNote(details);
            }
            editing.set(false);
            error.set("");
            dirty.set(false);
            dataChanges.markChanged();   // the streak, Today's check-in card and the list follow
            showEntry(saved);
            return true;
        } catch (DomainValidationException rejected) {
            error.set(messages.error(rejected));
            return false;
        } catch (NotFoundException gone) {
            error.set(messages.error(gone));
            return false;
        }
    }

    /** Selects {@code entry} in the list; if the filters hide it they are cleared first. */
    private void showEntry(JournalEntryView entry) {
        refreshEntries();
        if (entries.stream().noneMatch(candidate -> candidate.id().equals(entry.id()))) {
            kindFilter.set(KindFilter.ALL);
            goalFilter.set(null);
            areaFilter.set(null);
            refreshEntries();
        }
        entries.stream().filter(candidate -> candidate.id().equals(entry.id())).findFirst().ifPresent(selected::set);
    }

    /** Deletes the selected entry. */
    public void deleteSelected() {
        JournalEntryView entry = selected.get();
        if (entry == null) {
            return;
        }
        journal.delete(entry.id());
        selected.set(null);
        dataChanges.markChanged();
    }

    // ---- editor: state ----

    public ReadOnlyBooleanProperty editingProperty() {
        return editing.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<EntryKind> editingKindProperty() {
        return editingKind.getReadOnlyProperty();
    }

    /** True while the editor holds changes that were not saved. */
    public boolean isDirty() {
        return editing.get() && dirty.get();
    }

    public boolean isEditingExisting() {
        return editingId != null;
    }

    public ObjectProperty<LocalDate> editDateProperty() {
        return editDate;
    }

    public StringProperty editTitleProperty() {
        return editTitle;
    }

    public StringProperty editBodyProperty() {
        return editBody;
    }

    /** 1 to 5, or {@code null} while the check-in has none. */
    public ObjectProperty<Integer> editConfidenceProperty() {
        return editConfidence;
    }

    public ObjectProperty<AreaId> editAreaProperty() {
        return editArea;
    }

    public ObjectProperty<GoalId> editGoalProperty() {
        return editGoal;
    }

    public ObjectProperty<StepId> editStepProperty() {
        return editStep;
    }

    public ObservableList<GoalSummary> editGoals() {
        return editGoalsReadOnly;
    }

    public ObservableList<StepView> editSteps() {
        return editStepsReadOnly;
    }

    /** "New note", "Edit check-in", ...: the heading of the editor. */
    public ReadOnlyStringProperty editorTitleProperty() {
        return editorTitle.getReadOnlyProperty();
    }

    /** "Studied that week: 6.5 h" for a check-in; empty for a note. */
    public ReadOnlyStringProperty weekTextProperty() {
        return weekText.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }

    /** Save is enabled while the date is set and a note has text / a check-in has a confidence. */
    public BooleanBinding validProperty() {
        return valid;
    }

    // ---- helpers ----

    private void updateWeekText() {
        if (editingKind.get() != EntryKind.CHECK_IN || editDate.get() == null) {
            weekText.set("");
            return;
        }
        int minutes = journal.weekMinutes(editDate.get(), editGoal.get(), preferences.getWeekStart());
        weekText.set(messages.get("journal.weekStudied", StudyTime.hoursText(minutes)));
    }

    private List<GoalSummary> goalsOf(AreaId areaId) {
        List<GoalSummary> found = new ArrayList<>();
        if (areaId != null) {
            found.addAll(goalService.listGoals(areaId));
        } else {
            areaService.listAreas().forEach(area -> found.addAll(goalService.listGoals(area.id())));
        }
        return found;
    }

    private List<StepView> stepsOf(GoalId goalId) {
        if (goalId == null) {
            return List.of();
        }
        try {
            return goalService.getGoal(goalId).steps();
        } catch (NotFoundException gone) {
            return List.of();
        }
    }

    private java.util.Optional<GoalSummary> goalSummary(GoalId goalId) {
        return goalsOf(null).stream().filter(goal -> Objects.equals(goal.id(), goalId)).findFirst();
    }
}
