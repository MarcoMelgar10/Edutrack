package com.edutrack.desktop.ui.skills;

import com.edutrack.application.measure.MeasureView;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.measure.CombineRule;
import com.edutrack.domain.measure.Direction;
import com.edutrack.domain.skill.CompetencyId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code MeasureDialogView.fxml} to {@link MeasureDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class MeasureDialogController implements DialogForm<MeasureView> {

    private final MeasureDialogViewModel viewModel;

    @FXML
    private TextField nameField;
    @FXML
    private ComboBox<String> unitBox;
    @FXML
    private TextField maxField;
    @FXML
    private TextField targetField;
    @FXML
    private ComboBox<MeasureDialogViewModel.Choice<Direction>> directionBox;
    @FXML
    private ComboBox<MeasureDialogViewModel.Choice<CombineRule>> combineBox;
    @FXML
    private ComboBox<MeasureDialogViewModel.Choice<CompetencyId>> competencyBox;
    @FXML
    private Label numbersHint;
    @FXML
    private Label errorLabel;

    public MeasureDialogController(MeasureDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        unitBox.setItems(viewModel.unitSuggestions());
        unitBox.setEditable(true);
        unitBox.getEditor().textProperty().bindBidirectional(viewModel.unitProperty());
        maxField.textProperty().bindBidirectional(viewModel.maxValueProperty());
        targetField.textProperty().bindBidirectional(viewModel.targetProperty());
        directionBox.setItems(viewModel.directions());
        directionBox.valueProperty().bindBidirectional(viewModel.directionProperty());
        combineBox.setItems(viewModel.combineRules());
        combineBox.valueProperty().bindBidirectional(viewModel.combineProperty());
        competencyBox.setItems(viewModel.competencies());
        competencyBox.valueProperty().bindBidirectional(viewModel.competencyProperty());

        numbersHint.visibleProperty().bind(viewModel.numbersValidProperty().not());
        numbersHint.managedProperty().bind(numbersHint.visibleProperty());
        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(nameField::requestFocus);
    }

    public void create(GoalId goalId) {
        viewModel.create(goalId);
    }

    public void edit(MeasureView measure) {
        viewModel.edit(measure);
    }

    @Override
    public String title() {
        return viewModel.title();
    }

    @Override
    public String saveLabel() {
        return viewModel.saveLabel();
    }

    @Override
    public ObservableBooleanValue validProperty() {
        return viewModel.validProperty();
    }

    @Override
    public MeasureView submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
