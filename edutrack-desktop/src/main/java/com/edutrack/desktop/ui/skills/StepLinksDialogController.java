package com.edutrack.desktop.ui.skills;

import com.edutrack.application.goal.GoalService;
import com.edutrack.application.skill.CompetencyService;
import com.edutrack.application.skill.CompetencyView;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.StepId;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.CheckBoxListCell;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The "link steps" dialog of a competency: a checklist of the goal's roadmap steps; the checked ones are
 * the steps that build the competency. Saving replaces the links.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class StepLinksDialogController implements DialogForm<CompetencyView> {

    /** One step in the list and whether it is linked. */
    public record StepCheck(StepId id, String label, BooleanProperty linked) {
        @Override
        public String toString() {
            return label;
        }
    }

    private final CompetencyService competencyService;
    private final GoalService goalService;
    private final Messages messages;

    @FXML
    private ListView<StepCheck> stepList;

    private CompetencyView competency;
    private List<StepCheck> checks = List.of();

    public StepLinksDialogController(CompetencyService competencyService, GoalService goalService, Messages messages) {
        this.competencyService = competencyService;
        this.goalService = goalService;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        stepList.setCellFactory(CheckBoxListCell.forListView((StepCheck check) -> (ObservableValue<Boolean>) check.linked()));
    }

    public void show(CompetencyView shown) {
        competency = shown;
        Set<StepId> linked = new HashSet<>();
        shown.steps().forEach(step -> linked.add(step.id()));
        checks = goalService.getGoal(shown.goalId()).steps().stream()
                .map(step -> new StepCheck(step.id(), step.number() + ". " + step.title(),
                        new SimpleBooleanProperty(linked.contains(step.id()))))
                .toList();
        stepList.getItems().setAll(checks);
    }

    @Override
    public String title() {
        return messages.get("dialog.stepLinks.title", competency.name());
    }

    @Override
    public String saveLabel() {
        return messages.get("common.saveChanges");
    }

    @Override
    public ObservableBooleanValue validProperty() {
        return new SimpleBooleanProperty(true);
    }

    @Override
    public CompetencyView submit() {
        Set<StepId> chosen = new HashSet<>();
        checks.stream().filter(check -> check.linked().get()).forEach(check -> chosen.add(check.id()));
        return competencyService.setSteps(competency.id(), chosen);
    }

    @Override
    public void showError(String message) {
        // Linking steps cannot be rejected by the domain: every step belongs to the goal.
    }
}
