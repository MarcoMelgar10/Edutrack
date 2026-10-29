package com.edutrack.desktop.ui.files;

import atlantafx.base.theme.Styles;
import com.edutrack.application.attachment.AttachmentView;
import com.edutrack.desktop.ui.common.Formats;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.domain.goal.StepId;
import javafx.application.HostServices;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.DragEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Clock;
import java.util.List;

/**
 * The Files tab of a step: a list of the files kept with it, opened with the system's own program. Files are
 * added with the button or by dropping them from the file explorer onto the list.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class FilesPane {

    private final FilesViewModel viewModel;
    private final Confirmations confirmations;
    private final HostServices hostServices;
    private final Clock clock;
    private final Messages messages;

    private final VBox root = new VBox(10);
    private final ListView<AttachmentView> list = new ListView<>();

    public FilesPane(FilesViewModel viewModel, Confirmations confirmations, HostServices hostServices, Clock clock,
                     Messages messages) {
        this.viewModel = viewModel;
        this.confirmations = confirmations;
        this.hostServices = hostServices;
        this.clock = clock;
        this.messages = messages;

        Button add = new Button(messages.get("files.add"));
        add.getStyleClass().add(Styles.ACCENT);
        add.setGraphic(new FontIcon(Feather.PLUS));
        add.setOnAction(click -> chooseFiles());
        Label hint = new Label(messages.get("files.hint"));
        hint.getStyleClass().add("text-caption");
        hint.setWrapText(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(12, add, hint);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setWrapText(true);
        error.textProperty().bind(viewModel.errorProperty());
        error.visibleProperty().bind(viewModel.errorProperty().isNotEmpty());
        error.managedProperty().bind(error.visibleProperty());

        Label placeholder = new Label(messages.get("files.empty"));
        placeholder.getStyleClass().add("text-caption");
        placeholder.setWrapText(true);
        list.setPlaceholder(placeholder);
        list.getStyleClass().add("record-list");
        list.setItems(viewModel.files());
        list.setCellFactory(view -> new FileCell());
        VBox.setVgrow(list, Priority.ALWAYS);

        list.setOnDragOver(this::acceptDrag);
        list.setOnDragDropped(this::dropFiles);
        list.pseudoClassStateChanged(javafx.css.PseudoClass.getPseudoClass("drop"), false);

        root.getChildren().addAll(toolbar, error, list);
        root.setPadding(new Insets(12, 4, 16, 0));
        root.getStyleClass().add("goal-tab-body");
    }

    public void show(com.edutrack.domain.goal.GoalId goalId, StepId stepId) {
        viewModel.show(stepId);
    }

    public void clear() {
        viewModel.clear();
    }

    public Node content() {
        return root;
    }

    public ReadOnlyStringProperty tabTextProperty() {
        return viewModel.tabTextProperty();
    }

    // ---- adding ----

    private void chooseFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(messages.get("files.chooseTitle"));
        List<File> chosen = chooser.showOpenMultipleDialog(window());
        if (chosen != null && !chosen.isEmpty()) {
            viewModel.add(chosen.stream().map(File::toPath).toList());
        }
    }

    private void acceptDrag(DragEvent event) {
        if (event.getDragboard().hasFiles()) {
            event.acceptTransferModes(TransferMode.COPY);
        }
        event.consume();
    }

    private void dropFiles(DragEvent event) {
        boolean dropped = event.getDragboard().hasFiles();
        if (dropped) {
            viewModel.add(event.getDragboard().getFiles().stream().map(File::toPath).toList());
        }
        event.setDropCompleted(dropped);
        event.consume();
    }

    private Window window() {
        return root.getScene().getWindow();
    }

    // ---- rows ----

    private final class FileCell extends ListCell<AttachmentView> {

        @Override
        protected void updateItem(AttachmentView file, boolean empty) {
            super.updateItem(file, empty);
            if (empty || file == null) {
                setGraphic(null);
                return;
            }
            FontIcon icon = new FontIcon(Feather.FILE);
            icon.getStyleClass().add("routine-mark");
            Label name = new Label(file.name());
            name.getStyleClass().add("routine-title");
            Label detail = new Label(FilesViewModel.size(file.sizeBytes()) + " · "
                    + Formats.date(file.addedAt(), clock.getZone()));
            detail.getStyleClass().add("text-caption");
            VBox text = new VBox(2, name, detail);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button open = icon(Feather.EXTERNAL_LINK, messages.get("files.open"));
            open.setOnAction(click -> hostServices.showDocument(viewModel.pathOf(file.id()).toUri().toString()));
            Button delete = icon(Feather.TRASH_2, messages.get("common.delete"));
            delete.setOnAction(click -> {
                if (confirmations.confirmDelete(window(), "file", file.name(), null)) {
                    viewModel.delete(file.id());
                }
            });
            HBox row = new HBox(12, icon, text, open, delete);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }

        private Button icon(Feather glyph, String tip) {
            Button button = new Button();
            button.setGraphic(new FontIcon(glyph));
            button.getStyleClass().add("icon-button");
            button.setTooltip(new Tooltip(tip));
            button.setAccessibleText(tip);
            return button;
        }
    }
}
