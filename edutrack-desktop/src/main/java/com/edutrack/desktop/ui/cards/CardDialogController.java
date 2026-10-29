package com.edutrack.desktop.ui.cards;

import com.edutrack.application.review.CardView;
import com.edutrack.desktop.ui.common.StepChoice;
import com.edutrack.desktop.ui.dialog.DialogForm;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code CardDialogView.fxml} to {@link CardDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CardDialogController implements DialogForm<CardView> {

    private final CardDialogViewModel viewModel;

    @FXML
    private MarkdownField frontField;
    @FXML
    private MarkdownField backField;
    @FXML
    private TextField tagsField;
    @FXML
    private CheckBox reversibleBox;
    @FXML
    private ComboBox<StepChoice> stepBox;
    @FXML
    private Label errorLabel;

    public CardDialogController(CardDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        frontField.textProperty().bindBidirectional(viewModel.frontProperty());
        backField.textProperty().bindBidirectional(viewModel.backProperty());
        tagsField.textProperty().bindBidirectional(viewModel.tagsProperty());
        reversibleBox.selectedProperty().bindBidirectional(viewModel.reversibleProperty());

        stepBox.setItems(viewModel.steps());
        stepBox.valueProperty().bindBidirectional(viewModel.stepProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        // A card made from selected text already has its front: go straight to the back.
        Platform.runLater(() -> (viewModel.frontProperty().get().isBlank() ? frontField : backField).focusEditor());
    }

    /** A new card of the goal; {@code stepId} and {@code front} are optional starting values. */
    public void create(GoalId goalId, StepId stepId, String front) {
        viewModel.create(goalId, stepId, front);
    }

    public void edit(CardView card) {
        viewModel.edit(card);
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
    public CardView submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
