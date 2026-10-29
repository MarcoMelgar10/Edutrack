package com.edutrack.desktop.ui.dialog;

import atlantafx.base.theme.Styles;
import com.edutrack.desktop.ui.common.AppIcons;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.desktop.ui.theme.ThemeManager;
import com.edutrack.domain.shared.DomainValidationException;
import javafx.event.ActionEvent;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Runs every create/edit dialog the same way: themed, fading in, primary button disabled until
 * the form is valid, domain validation messages shown inline without closing the dialog, and a
 * {@link DataChanges} signal after a successful save.
 */
@Component
public class FormDialogs {

    private final ViewLoader viewLoader;
    private final ThemeManager themeManager;
    private final DataChanges dataChanges;
    private final Messages messages;

    public FormDialogs(ViewLoader viewLoader, ThemeManager themeManager, DataChanges dataChanges,
                       Messages messages) {
        this.viewLoader = viewLoader;
        this.themeManager = themeManager;
        this.dataChanges = dataChanges;
        this.messages = messages;
    }

    /**
     * @param formType controller class of the dialog (its FXML sits next to it)
     * @param prepare  puts the form in create or edit mode before it shows, e.g. {@code f -> f.edit(area)}
     * @return the saved result, or empty if the user cancelled
     */
    public <F extends DialogForm<R>, R> Optional<R> show(Window owner, Class<F> formType, Consumer<F> prepare) {
        ViewLoader.LoadedView<F> view = viewLoader.load(formType);
        F form = view.controller();
        prepare.accept(form);

        Dialog<R> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(form.title());
        dialog.setHeaderText(form.title());

        DialogPane pane = dialog.getDialogPane();
        pane.setContent(view.root());
        pane.getStyleClass().add("form-dialog");
        ButtonType saveType = new ButtonType(form.saveLabel(), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType(messages.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
        pane.getButtonTypes().setAll(saveType, cancelType);

        Button save = (Button) pane.lookupButton(saveType);
        save.getStyleClass().add(Styles.ACCENT);
        save.disableProperty().bind(javafx.beans.binding.Bindings.not(form.validProperty()));

        AtomicReference<R> saved = new AtomicReference<>();
        save.addEventFilter(ActionEvent.ACTION, click -> {
            try {
                saved.set(form.submit());
            } catch (DomainValidationException rejected) {
                form.showError(messages.error(rejected));
                click.consume();
            }
        });
        dialog.setResultConverter(button -> button == saveType ? saved.get() : null);

        themeManager.style(pane);
        if (pane.getScene().getWindow() instanceof Stage stage) {
            stage.getIcons().setAll(AppIcons.all());
        }
        dialog.setOnShowing(showing -> Motion.fadeIn(pane.getScene().getWindow()));

        Optional<R> result = dialog.showAndWait();
        result.ifPresent(value -> dataChanges.markChanged());
        return result;
    }
}
