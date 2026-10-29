package com.edutrack.desktop.ui.session;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.session.SessionCommands;
import com.edutrack.application.session.SessionService;
import com.edutrack.application.session.SessionView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.practice.PracticeRoutineId;
import com.edutrack.domain.session.SessionSource;
import com.edutrack.domain.session.StudySession;
import com.edutrack.domain.session.StudySessionId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * State of the Log session dialog. {@link #create(GoalId, StepId)} starts a manual entry (the goal and
 * step may be preselected), {@link #prefill} fills it from a session the timer left behind, and
 * {@link #edit(SessionView)} changes a logged one (its goal cannot change). The start is a date and an
 * "HH:mm" time in the system zone; the minutes are typed as a whole number from 1 to 1440.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class LogSessionDialogViewModel {

    private static final DateTimeFormatter TIME = new DateTimeFormatterBuilder()
            .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
            .toFormatter()
            .withResolverStyle(ResolverStyle.STRICT);

    private final SessionService sessionService;
    private final GoalService goalService;
    private final Clock clock;
    private final Messages messages;

    private final ObservableList<GoalChoice> goals = FXCollections.observableArrayList();
    private final ObservableList<StepChoice> steps = FXCollections.observableArrayList();
    private final ObjectProperty<GoalChoice> goal = new SimpleObjectProperty<>();
    private final ObjectProperty<StepChoice> step = new SimpleObjectProperty<>();
    private final ObjectProperty<LocalDate> date = new SimpleObjectProperty<>();
    private final StringProperty time = new SimpleStringProperty("");
    private final StringProperty minutes = new SimpleStringProperty("");
    private final StringProperty note = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final ReadOnlyBooleanWrapper goalLocked = new ReadOnlyBooleanWrapper(false);
    private final BooleanBinding minutesValid;
    private final BooleanBinding timeValid;
    private final BooleanBinding valid;

    private StudySessionId editing;
    private PracticeRoutineId routineId;
    private SessionSource source = SessionSource.MANUAL;

    public LogSessionDialogViewModel(SessionService sessionService, AreaService areaService, GoalService goalService,
                                     Clock clock, Messages messages) {
        this.sessionService = sessionService;
        this.goalService = goalService;
        this.clock = clock;
        this.messages = messages;
        goals.setAll(SessionChoices.goals(areaService, goalService));
        goal.subscribe(this::loadSteps);
        this.minutesValid = Bindings.createBooleanBinding(() -> parseMinutes(minutes.get()) != null, minutes);
        this.timeValid = Bindings.createBooleanBinding(() -> parseTime(time.get()) != null, time);
        this.valid = Bindings.createBooleanBinding(
                () -> goal.get() != null && date.get() != null && timeValid.get() && minutesValid.get(),
                goal, date, timeValid, minutesValid);
    }

    // ---- modes ----

    /** A new manual entry dated today at the current time; {@code goalId} and {@code stepId} may be {@code null}. */
    public void create(GoalId goalId, StepId stepId) {
        editing = null;
        routineId = null;
        source = SessionSource.MANUAL;
        selectGoal(goalId);
        selectStep(stepId);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        date.set(now.toLocalDate());
        time.set(TIME.format(now));
    }

    /** An entry for a session the timer could not save, e.g. after the app was closed while timing. */
    public void prefill(GoalId goalId, StepId stepId, PracticeRoutineId routine, Instant startedAt, int minutesStudied,
                        SessionSource origin) {
        editing = null;
        routineId = routine;
        source = origin == null ? SessionSource.MANUAL : origin;
        selectGoal(goalId);
        selectStep(stepId);
        LocalDateTime start = LocalDateTime.ofInstant(startedAt, clock.getZone());
        date.set(start.toLocalDate());
        time.set(TIME.format(start));
        minutes.set(String.valueOf(Math.min(StudySession.MAX_MINUTES, Math.max(StudySession.MIN_MINUTES, minutesStudied))));
    }

    public void edit(SessionView session) {
        editing = session.id();
        routineId = session.routineId();
        source = session.source();
        goalLocked.set(true);
        selectGoal(session.goalId());
        selectStep(session.stepId());
        LocalDateTime start = LocalDateTime.ofInstant(session.startedAt(), clock.getZone());
        date.set(start.toLocalDate());
        time.set(TIME.format(start));
        minutes.set(String.valueOf(session.minutes()));
        note.set(session.note() == null ? "" : session.note());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.session.new" : "dialog.session.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.session.log" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public SessionView submit() {
        error.set("");
        Instant startedAt = startedAt();
        Integer studied = parseMinutes(minutes.get());
        StepId stepId = step.get() == null ? null : step.get().id();
        if (editing != null) {
            return sessionService.update(new SessionCommands.UpdateSession(editing, stepId, startedAt, studied,
                    note.get()));
        }
        return sessionService.log(new SessionCommands.LogSession(goal.get().id(), stepId, routineId, startedAt,
                studied, note.get(), source));
    }

    /** The start as typed: the date and time in the system zone. */
    Instant startedAt() {
        LocalTime typed = parseTime(time.get());
        return LocalDateTime.of(date.get(), typed).atZone(clock.getZone()).toInstant();
    }

    // ---- goal and steps ----

    private void selectGoal(GoalId goalId) {
        goal.set(goalId == null ? null : goals.stream().filter(choice -> choice.id().equals(goalId))
                .findFirst().orElse(null));
        if (goal.get() == null) {
            loadSteps(null);
        }
    }

    private void selectStep(StepId stepId) {
        step.set(steps.stream().filter(choice -> Objects.equals(choice.id(), stepId))
                .findFirst().orElse(steps.isEmpty() ? null : steps.getFirst()));
    }

    /** "No specific step" followed by the goal's steps; the previous choice is dropped. */
    private void loadSteps(GoalChoice chosen) {
        steps.setAll(SessionChoices.steps(goalService, chosen == null ? null : chosen.id(), messages));
        step.set(steps.getFirst());
    }

    // ---- parsing ----

    /** The whole number of minutes, or {@code null} when the text is not a number from 1 to 1440. */
    static Integer parseMinutes(String text) {
        if (text == null || !text.strip().matches("\\d{1,4}")) {
            return null;
        }
        int value = Integer.parseInt(text.strip());
        return value >= StudySession.MIN_MINUTES && value <= StudySession.MAX_MINUTES ? value : null;
    }

    static LocalTime parseTime(String text) {
        if (text == null) {
            return null;
        }
        try {
            return LocalTime.parse(text.strip(), TIME);
        } catch (DateTimeParseException notATime) {
            return null;
        }
    }

    // ---- properties ----

    public ObservableList<GoalChoice> goals() {
        return goals;
    }

    public ObservableList<StepChoice> steps() {
        return steps;
    }

    public ObjectProperty<GoalChoice> goalProperty() {
        return goal;
    }

    public ObjectProperty<StepChoice> stepProperty() {
        return step;
    }

    public ObjectProperty<LocalDate> dateProperty() {
        return date;
    }

    /** The start time typed as "HH:mm". */
    public StringProperty timeProperty() {
        return time;
    }

    public StringProperty minutesProperty() {
        return minutes;
    }

    public StringProperty noteProperty() {
        return note;
    }

    public StringProperty errorProperty() {
        return error;
    }

    /** True when editing a logged session: it stays on its goal. */
    public ReadOnlyBooleanProperty goalLockedProperty() {
        return goalLocked.getReadOnlyProperty();
    }

    public BooleanBinding timeValidProperty() {
        return timeValid;
    }

    public BooleanBinding minutesValidProperty() {
        return minutesValid;
    }

    public BooleanBinding validProperty() {
        return valid;
    }

    /** The last day a session can start on. */
    public LocalDate today() {
        return LocalDate.now(clock);
    }
}
