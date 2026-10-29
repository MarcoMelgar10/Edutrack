package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.common.AreaIcons;
import com.edutrack.domain.area.AreaIcon;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.TilePane;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code AreaDialogView.fxml} to {@link AreaDialogViewModel}. Reference pattern for all form dialogs. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class AreaDialogController implements DialogForm<AreaSummary> {

    private final AreaDialogViewModel viewModel;

    @FXML
    private TextField nameField;
    @FXML
    private TextArea descriptionField;
    @FXML
    private TilePane iconPicker;
    @FXML
    private Label errorLabel;

    public AreaDialogController(AreaDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        nameField.textProperty().bindBidirectional(viewModel.nameProperty());
        descriptionField.textProperty().bindBidirectional(viewModel.descriptionProperty());
        buildIconPicker();

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(nameField::requestFocus);
    }

    /** One toggle per curated icon; exactly one stays selected (clicking the selected one does nothing). */
    private void buildIconPicker() {
        ToggleGroup group = new ToggleGroup();
        for (AreaIcon icon : AreaIcon.values()) {
            ToggleButton choice = new ToggleButton();
            choice.setGraphic(AreaIcons.icon(icon));
            choice.setUserData(icon);
            choice.setToggleGroup(group);
            choice.setTooltip(new Tooltip(AreaIcons.label(icon)));
            choice.setAccessibleText(AreaIcons.label(icon));
            choice.getStyleClass().add("icon-choice");
            choice.addEventFilter(MouseEvent.MOUSE_PRESSED, press -> {
                if (choice.isSelected()) {
                    press.consume();
                }
            });
            iconPicker.getChildren().add(choice);
        }
        viewModel.iconProperty().subscribe(icon -> group.getToggles().stream()
                .filter(toggle -> toggle.getUserData() == icon)
                .findFirst()
                .ifPresent(group::selectToggle));
        group.selectedToggleProperty().subscribe(selected -> {
            if (selected != null) {
                viewModel.iconProperty().set((AreaIcon) selected.getUserData());
            }
        });
    }

    public void edit(AreaSummary area) {
        viewModel.edit(area);
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
    public AreaSummary submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
