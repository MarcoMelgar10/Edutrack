package com.edutrack.desktop.ui.skills;

import com.edutrack.application.measure.MeasureService;
import com.edutrack.application.measure.MeasureView;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.measure.MeasureDetails;
import com.edutrack.domain.skill.CompetencyId;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * State of the measure dialog. A measure is something tracked with numbers over time on a goal (an exam band,
 * words per minute, a tempo): a name, a free unit, an optional maximum and target, whether higher or lower is
 * better, how the results of one attempt are combined and, optionally, the competency it shows progress on.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MeasureDialogViewModel {

    private static final Pattern NUMBER = Pattern.compile("\\d{1,6}([.,]\\d{1,2})?");

    /** An entry of one of the boxes. */
    public record Choice<T>(T value, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final MeasureService measureService;
    private final CompetencyService competencyService;
    private final Messages messages;

    private final ObservableList<String> unitSuggestions;
    private final ObservableList<Choice<Direction>> directions;
    private final ObservableList<Choice<CombineRule>> combineRules;
    private final ObservableList<Choice<CompetencyId>> competencies = FXCollections.observableArrayList();
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty unit = new SimpleStringProperty("");
    private final StringProperty maxValue = new SimpleStringProperty("");
    private final StringProperty target = new SimpleStringProperty("");
    private final ObjectProperty<Choice<Direction>> direction = new SimpleObjectProperty<>();
    private final ObjectProperty<Choice<CombineRule>> combine = new SimpleObjectProperty<>();
    private final ObjectProperty<Choice<CompetencyId>> competency = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding numbersValid;
    private final BooleanBinding valid;

    private GoalId goalId;
    private MeasureView editing;

    public MeasureDialogViewModel(MeasureService measureService, CompetencyService competencyService,
                                  Messages messages) {
        this.measureService = measureService;
        this.competencyService = competencyService;
        this.messages = messages;
        this.unitSuggestions = FXCollections.observableArrayList("band", "%", "pts", "min", "bpm",
                messages.get("measure.unit.words"));
        this.directions = FXCollections.observableArrayList(
                new Choice<>(Direction.HIGHER_IS_BETTER, messages.get("measure.direction.higher")),
                new Choice<>(Direction.LOWER_IS_BETTER, messages.get("measure.direction.lower")));
        this.combineRules = FXCollections.observableArrayList(
                new Choice<>(CombineRule.NONE, messages.get("measure.combine.none")),
                new Choice<>(CombineRule.AVERAGE, messages.get("measure.combine.average")),
                new Choice<>(CombineRule.SUM, messages.get("measure.combine.sum")));
        direction.set(directions.getFirst());
        combine.set(combineRules.getFirst());
        this.numbersValid = Bindings.createBooleanBinding(
                () -> blankOrNumber(maxValue.get()) && blankOrNumber(target.get()), maxValue, target);
        this.valid = Bindings.createBooleanBinding(() -> !name.get().isBlank() && numbersValid.get(), name, numbersValid);
    }

    public void create(GoalId goal) {
        goalId = goal;
        editing = null;
        loadCompetencies(null);
    }

    public void edit(MeasureView measure) {
        goalId = measure.goalId();
        editing = measure;
        loadCompetencies(measure.competencyId());
        name.set(measure.name());
        unit.set(measure.unit() == null ? "" : measure.unit());
        maxValue.set(measure.maxValue() == null ? "" : Formats.number(measure.maxValue()));
        target.set(measure.target() == null ? "" : Formats.number(measure.target()));
        direction.set(directions.stream().filter(choice -> choice.value() == measure.direction()).findFirst().orElseThrow());
        combine.set(combineRules.stream().filter(choice -> choice.value() == measure.combineRule()).findFirst().orElseThrow());
    }

    public String title() {
        return messages.get(editing == null ? "dialog.measure.new" : "dialog.measure.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.measure.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public MeasureView submit() {
        error.set("");
        MeasureDetails details = new MeasureDetails(name.get(), unit.get(), number(maxValue.get()), number(target.get()),
                direction.get().value(), combine.get().value(), competency.get() == null ? null : competency.get().value());
        return editing == null ? measureService.create(goalId, details) : measureService.update(editing.id(), details);
    }

    private void loadCompetencies(CompetencyId selected) {
        List<Choice<CompetencyId>> choices = new ArrayList<>();
        choices.add(new Choice<>(null, messages.get("measure.competency.none")));
        competencyService.forGoal(goalId).forEach(each -> choices.add(new Choice<>(each.id(), each.name())));
        competencies.setAll(choices);
        competency.set(choices.stream().filter(choice -> Objects.equals(choice.value(), selected)).findFirst()
                .orElse(choices.getFirst()));
    }

    private static boolean blankOrNumber(String text) {
        return text == null || text.isBlank() || NUMBER.matcher(text.strip()).matches();
    }

    private static BigDecimal number(String text) {
        return text == null || text.isBlank() ? null : new BigDecimal(text.strip().replace(',', '.'));
    }

    public ObservableList<String> unitSuggestions() {
        return unitSuggestions;
    }

    public ObservableList<Choice<Direction>> directions() {
        return directions;
    }

    public ObservableList<Choice<CombineRule>> combineRules() {
        return combineRules;
    }

    public ObservableList<Choice<CompetencyId>> competencies() {
        return competencies;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty unitProperty() {
        return unit;
    }

    public StringProperty maxValueProperty() {
        return maxValue;
    }

    public StringProperty targetProperty() {
        return target;
    }

    public ObjectProperty<Choice<Direction>> directionProperty() {
        return direction;
    }

    public ObjectProperty<Choice<CombineRule>> combineProperty() {
        return combine;
    }

    public ObjectProperty<Choice<CompetencyId>> competencyProperty() {
        return competency;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding numbersValidProperty() {
        return numbersValid;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
