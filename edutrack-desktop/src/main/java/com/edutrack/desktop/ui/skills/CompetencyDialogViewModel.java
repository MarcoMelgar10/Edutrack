package com.edutrack.desktop.ui.skills;

import com.edutrack.application.skill.CompetencyService;
import com.edutrack.application.skill.CompetencyView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.skill.CompetencyDetails;
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

import java.util.stream.IntStream;

/** State of the competency dialog: a name, a description and the level the learner aims for (1 to 5). */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompetencyDialogViewModel {

    /** An entry of the target-level box: "4 · Confident". */
    public record LevelChoice(int level, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final CompetencyService competencyService;
    private final Messages messages;

    private final ObservableList<LevelChoice> levels;
    private final StringProperty name = new SimpleStringProperty("");
    private final StringProperty description = new SimpleStringProperty("");
    private final ObjectProperty<LevelChoice> target = new SimpleObjectProperty<>();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;

    private GoalId goalId;
    private CompetencyView editing;

    public CompetencyDialogViewModel(CompetencyService competencyService, Messages messages) {
        this.competencyService = competencyService;
        this.messages = messages;
        this.levels = FXCollections.observableArrayList(IntStream.rangeClosed(1, 5)
                .mapToObj(level -> new LevelChoice(level, level + " · " + messages.get("skill.level." + level))).toList());
        target.set(levels.get(3)); // "Confident" is the usual aim
        this.valid = Bindings.createBooleanBinding(() -> !name.get().isBlank() && target.get() != null, name, target);
    }

    public void create(GoalId goal) {
        goalId = goal;
        editing = null;
    }

    public void edit(CompetencyView competency) {
        goalId = competency.goalId();
        editing = competency;
        name.set(competency.name());
        description.set(competency.description() == null ? "" : competency.description());
        target.set(levels.get(competency.targetLevel() - 1));
    }

    public String title() {
        return messages.get(editing == null ? "dialog.competency.new" : "dialog.competency.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.competency.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public CompetencyView submit() {
        error.set("");
        CompetencyDetails details = new CompetencyDetails(name.get(), description.get(), target.get().level());
        return editing == null ? competencyService.create(goalId, details)
                : competencyService.update(editing.id(), details);
    }

    public ObservableList<LevelChoice> levels() {
        return levels;
    }

    public StringProperty nameProperty() {
        return name;
    }

    public StringProperty descriptionProperty() {
        return description;
    }

    public ObjectProperty<LevelChoice> targetProperty() {
        return target;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
