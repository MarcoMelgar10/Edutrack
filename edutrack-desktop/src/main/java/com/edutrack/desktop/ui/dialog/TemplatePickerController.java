package com.edutrack.desktop.ui.dialog;

import com.edutrack.desktop.ui.common.ActionResult;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import javafx.beans.value.ObservableBooleanValue;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;

/** Binds {@code TemplatePickerView.fxml} to {@link TemplatePickerViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class TemplatePickerController implements DialogForm<GoalId> {

    private final TemplatePickerViewModel viewModel;
    private final Messages messages;

    @FXML
    private VBox root;
    @FXML
    private ListView<TemplatePickerViewModel.Item> templateList;
    @FXML
    private Label descriptionLabel;
    @FXML
    private Label statusLabel;
    @FXML
    private Label errorLabel;

    public TemplatePickerController(TemplatePickerViewModel viewModel, Messages messages) {
        this.viewModel = viewModel;
        this.messages = messages;
    }

    @FXML
    private void initialize() {
        templateList.setItems(viewModel.items());
        templateList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(TemplatePickerViewModel.Item item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name() + " · " + viewModel.stepsText(item));
            }
        });
        templateList.getSelectionModel().selectedItemProperty().subscribe(viewModel.selectedProperty()::set);
        viewModel.selectedProperty().subscribe(item -> {
            if (templateList.getSelectionModel().getSelectedItem() != item) {
                templateList.getSelectionModel().select(item);
            }
        });
        descriptionLabel.textProperty().bind(viewModel.selectedProperty().map(
                TemplatePickerViewModel.Item::description).orElse(""));
        statusLabel.textProperty().bind(viewModel.statusProperty());
        statusLabel.visibleProperty().bind(viewModel.statusProperty().isNotEmpty());
        statusLabel.managedProperty().bind(statusLabel.visibleProperty());
        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());
    }

    public void create(AreaId areaId) {
        viewModel.create(areaId);
    }

    @FXML
    private void importTemplate() {
        File file = chooser("template.chooser.import").showOpenDialog(root.getScene().getWindow());
        if (file != null) {
            markStatus(viewModel.importFile(file.toPath()));
        }
    }

    @FXML
    private void exportTemplate() {
        if (viewModel.selectedProperty().get() == null) {
            markStatus(viewModel.exportSelected(null));
            return;
        }
        FileChooser chooser = chooser("template.chooser.export");
        chooser.setInitialFileName(viewModel.suggestedFileName());
        File file = chooser.showSaveDialog(root.getScene().getWindow());
        if (file != null) {
            markStatus(viewModel.exportSelected(file.toPath()));
        }
    }

    private FileChooser chooser(String titleKey) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get(titleKey));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(messages.get("template.filter"), "*.json"));
        return chooser;
    }

    private void markStatus(ActionResult result) {
        statusLabel.getStyleClass().remove("form-error");
        if (!result.success()) {
            statusLabel.getStyleClass().add("form-error");
        }
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
    public GoalId submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
