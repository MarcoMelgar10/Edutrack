package com.edutrack.desktop.ui.dialog;

import com.edutrack.application.record.RecordView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.domain.record.RecordOwner;
import com.edutrack.domain.record.ResourceType;
import javafx.application.Platform;
import javafx.beans.value.ObservableBooleanValue;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.util.StringConverter;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/** Binds {@code ResourceDialogView.fxml} to {@link ResourceDialogViewModel}. */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ResourceDialogController implements DialogForm<RecordView> {

    private final ResourceDialogViewModel viewModel;

    @FXML
    private TextField titleField;
    @FXML
    private TextField urlField;
    @FXML
    private ComboBox<ResourceType> typeBox;
    @FXML
    private Label errorLabel;

    public ResourceDialogController(ResourceDialogViewModel viewModel) {
        this.viewModel = viewModel;
    }

    @FXML
    private void initialize() {
        titleField.textProperty().bindBidirectional(viewModel.resourceTitleProperty());
        urlField.textProperty().bindBidirectional(viewModel.urlProperty());

        typeBox.setItems(FXCollections.observableArrayList(ResourceType.values()));
        typeBox.setConverter(new StringConverter<>() {
            @Override
            public String toString(ResourceType type) {
                return type == null ? "" : Formats.resourceType(type);
            }

            @Override
            public ResourceType fromString(String text) {
                return null; // the combo box is not editable
            }
        });
        typeBox.valueProperty().bindBidirectional(viewModel.typeProperty());

        errorLabel.textProperty().bind(viewModel.errorProperty());
        errorLabel.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        errorLabel.managedProperty().bind(errorLabel.visibleProperty());

        Platform.runLater(titleField::requestFocus);
    }

    public void create(RecordOwner owner) {
        viewModel.create(owner);
    }

    public void edit(RecordOwner owner, RecordView.ResourceView resource) {
        viewModel.edit(owner, resource);
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
    public RecordView submit() {
        return viewModel.submit();
    }

    @Override
    public void showError(String message) {
        viewModel.errorProperty().set(message);
    }
}
