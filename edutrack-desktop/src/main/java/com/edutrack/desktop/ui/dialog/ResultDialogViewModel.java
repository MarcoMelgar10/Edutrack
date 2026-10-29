package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.record.LearningRecordService;
import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.record.AssessmentDetails;
import com.edutrack.domain.record.AssessmentId;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.MeasureId;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.time.Clock;
import java.time.LocalDate;
import java.util.regex.Pattern;

/**
 * State of the Result dialog (a test, exam or certificate). Call {@link #create(RecordOwner)} for a new
 * result, dated today, or {@link #edit(RecordOwner, RecordView.ResultView)} to change an existing one.
 * Score and maximum are typed as text: "," and "." both work as the decimal separator and the
 * maximum may stay blank.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ResultDialogViewModel {

    /** English text of the rule; the UI shows the translation of {@code error.invalid-score}. */
    static final String INVALID_SCORE = "Enter scores as numbers, e.g. 6.5";

    private static final Pattern NUMBER = Pattern.compile("\\d+[.,]?\\d*|[.,]\\d+");

    /** An entry of the measure box; {@code view} is {@code null} for "No measure". */
    public record MeasureChoice(MeasureId id, String label, MeasureView view) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final LearningRecordService records;
    private final MeasureService measureService;
    private final Clock clock;
    private final Messages messages;

    private final StringProperty resultTitle = new SimpleStringProperty("");
    private final StringProperty score = new SimpleStringProperty("");
    private final StringProperty maxScore = new SimpleStringProperty("");
    private final ObjectProperty<LocalDate> takenOn = new SimpleObjectProperty<>();
    private final StringProperty note = new SimpleStringProperty("");
    private final StringProperty error = new SimpleStringProperty("");
    private final ObservableList<MeasureChoice> measures = FXCollections.observableArrayList();
    private final ObjectProperty<MeasureChoice> measure = new SimpleObjectProperty<>();
    private final StringProperty part = new SimpleStringProperty("");
    private final BooleanBinding partEnabled;
    private final BooleanBinding scoreValid;
    private final BooleanBinding valid;
    private RecordOwner owner;
    private AssessmentId editing;

    public ResultDialogViewModel(LearningRecordService records, MeasureService measureService, Clock clock,
                                 Messages messages) {
        this.records = records;
        this.measureService = measureService;
        this.clock = clock;
        this.messages = messages;
        this.scoreValid = Bindings.createBooleanBinding(() -> {
            try {
                return parse(score.get()) != null && (isBlank(maxScore.get()) || parse(maxScore.get()) != null);
            } catch (NumberFormatException notANumber) {
                return false;
            }
        }, score, maxScore);
        this.valid = Bindings.createBooleanBinding(
                () -> !isBlank(resultTitle.get()) && scoreValid.get(), resultTitle, scoreValid);
        this.partEnabled = Bindings.createBooleanBinding(() -> measure.get() != null && measure.get().view() != null
                && measure.get().view().combineRule() != CombineRule.NONE, measure);
        // Choosing a measure with a maximum fills the maximum in, unless one was typed.
        measure.subscribe(choice -> {
            if (choice != null && choice.view() != null && choice.view().maxValue() != null && isBlank(maxScore.get())) {
                maxScore.set(Formats.number(choice.view().maxValue()));
            }
            if (!partEnabled.get()) {
                part.set("");
            }
        });
    }

    public void create(RecordOwner owner) {
        create(owner, null);
    }

    /** A new result; {@code goalId} (the goal of the step or of the results) offers that goal's measures. */
    public void create(RecordOwner owner, GoalId goalId) {
        this.owner = owner;
        this.editing = null;
        takenOn.set(LocalDate.now(clock));
        loadMeasures(goalId, null);
    }

    public void edit(RecordOwner owner, RecordView.ResultView result) {
        edit(owner, null, result);
    }

    public void edit(RecordOwner owner, GoalId goalId, RecordView.ResultView result) {
        this.owner = owner;
        loadMeasures(goalId, result.measureId());
        part.set(result.part() == null ? "" : result.part());
        editing = result.id();
        resultTitle.set(result.title());
        score.set(Formats.number(result.score()));
        maxScore.set(result.maxScore() == null ? "" : Formats.number(result.maxScore()));
        takenOn.set(result.takenOn());
        note.set(result.note() == null ? "" : result.note());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.result.new" : "dialog.result.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.result.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public RecordView submit() {
        error.set("");
        BigDecimal scoreValue;
        BigDecimal maxValue;
        try {
            scoreValue = parse(score.get());
            maxValue = parse(maxScore.get());
        } catch (NumberFormatException notANumber) {
            throw new DomainValidationException("invalid-score", INVALID_SCORE);
        }
        if (scoreValue == null) {
            throw new DomainValidationException("invalid-score", INVALID_SCORE);
        }
        MeasureId measureId = measure.get() == null ? null : measure.get().id();
        AssessmentDetails details = new AssessmentDetails(resultTitle.get(), scoreValue, maxValue, takenOn.get(),
                note.get(), measureId, partEnabled.get() ? part.get() : null);
        return editing == null
                ? records.addResult(owner, details)
                : records.updateResult(owner, editing, details);
    }

    /** "No measure" followed by the goal's measures; the result's own measure is selected when editing. */
    private void loadMeasures(GoalId goalId, MeasureId selected) {
        List<MeasureChoice> choices = new ArrayList<>();
        choices.add(new MeasureChoice(null, messages.get("dialog.result.noMeasure"), null));
        if (goalId != null) {
            for (MeasureView view : measureService.forGoal(goalId)) {
                choices.add(new MeasureChoice(view.id(), view.name() + (view.unit() == null ? "" : " (" + view.unit() + ")"), view));
            }
        }
        measures.setAll(choices);
        measure.set(choices.stream().filter(choice -> Objects.equals(choice.id(), selected)).findFirst()
                .orElse(choices.getFirst()));
    }

    /** The measure box is only worth showing when the goal has measures. */
    public boolean hasMeasures() {
        return measures.size() > 1;
    }

    public ObservableList<MeasureChoice> measures() {
        return measures;
    }

    public ObjectProperty<MeasureChoice> measureProperty() {
        return measure;
    }

    /** The part of an attempt this result is, e.g. "Listening"; only for measures that combine parts. */
    public StringProperty partProperty() {
        return part;
    }

    public BooleanBinding partEnabledProperty() {
        return partEnabled;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    /** Blank text means "no value" ({@code null}); anything but a non-negative number throws. */
    private static BigDecimal parse(String text) {
        if (isBlank(text)) {
            return null;
        }
        String typed = text.strip();
        if (!NUMBER.matcher(typed).matches()) {
            throw new NumberFormatException("Not a number: " + typed);
        }
        return new BigDecimal(typed.replace(',', '.'));
    }

    public StringProperty resultTitleProperty() {
        return resultTitle;
    }

    /** The text of the Score field. */
    public StringProperty scoreProperty() {
        return score;
    }

    /** The text of the Out of field; blank means the result has no maximum. */
    public StringProperty maxScoreProperty() {
        return maxScore;
    }

    public ObjectProperty<LocalDate> takenOnProperty() {
        return takenOn;
    }

    public StringProperty noteProperty() {
        return note;
    }

    public StringProperty errorProperty() {
        return error;
    }

    /** False while the score is empty or either number does not parse. */
    public BooleanBinding scoreValidProperty() {
        return scoreValid;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
