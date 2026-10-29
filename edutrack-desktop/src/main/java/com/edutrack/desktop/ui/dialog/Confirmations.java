package com.edutrack.desktop.ui.dialog;

import atlantafx.base.theme.Styles;
import com.edutrack.desktop.ui.common.AppIcons;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.desktop.ui.theme.ThemeManager;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

/**
 * Asks before anything is deleted or undone. Cancel is the default button, so Enter never
 * deletes or reopens anything by accident.
 */
@Component
public class Confirmations {

    private final ThemeManager themeManager;
    private final Messages messages;

    public Confirmations(ThemeManager themeManager, Messages messages) {
        this.themeManager = themeManager;
        this.messages = messages;
    }

    /**
     * @param kind        what is deleted: {@code area}, {@code goal}, {@code step}, {@code resource},
     *                    {@code exercise} or {@code result} (the {@code delete.header.<kind>} text)
     * @param name        its name, shown in quotes
     * @param consequence what else goes with it, e.g. "Its 3 roadmap steps will be deleted too."; may be {@code null}
     */
    public boolean confirmDelete(Window owner, String kind, String name, String consequence) {
        return confirmDelete(owner, messages.get("delete.header." + kind, name), consequence);
    }

    /**
     * @param header      the whole question, e.g. "Delete goal “IELTS 7.0”?"
     * @param consequence what else goes with it; may be {@code null}
     */
    public boolean confirmDelete(Window owner, String header, String consequence) {
        ButtonType delete = new ButtonType(messages.get("common.delete"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = cancelButton();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", delete, cancel);
        alert.initOwner(owner);
        alert.setTitle(messages.get("common.delete"));
        alert.setHeaderText(header);
        alert.setContentText((consequence == null ? "" : consequence + " ") + messages.get("delete.cannotUndo"));
        alert.setGraphic(null);

        ((Button) alert.getDialogPane().lookupButton(delete)).getStyleClass().add(Styles.DANGER);
        return show(alert, delete, cancel);
    }

    /**
     * Asked before a Done step goes back to Not started, which clears its completion date.
     *
     * @param completedOn the formatted completion date, or empty when unknown
     */
    public boolean confirmReopen(Window owner, String stepTitle, String completedOn) {
        ButtonType reopen = new ButtonType(messages.get("reopen.button"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = cancelButton();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", reopen, cancel);
        alert.initOwner(owner);
        alert.setTitle(messages.get("reopen.title"));
        alert.setHeaderText(messages.get("reopen.header", stepTitle));
        boolean dated = completedOn != null && !completedOn.isEmpty();
        alert.setContentText(dated ? messages.get("reopen.contentDated", completedOn) : messages.get("reopen.content"));
        alert.setGraphic(null);
        ((Button) alert.getDialogPane().lookupButton(reopen)).getStyleClass().add(Styles.ACCENT);
        return show(alert, reopen, cancel);
    }

    /**
     * Asked before a backup replaces everything in the app.
     *
     * @param madeOn the formatted date of the backup
     */
    public boolean confirmRestore(Window owner, String madeOn, int records, int files) {
        ButtonType restore = new ButtonType(messages.get("restore.button"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = cancelButton();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", restore, cancel);
        alert.initOwner(owner);
        alert.setTitle(messages.get("restore.title"));
        alert.setHeaderText(messages.get("restore.header", madeOn));
        alert.setContentText(messages.get("restore.content", records, files));
        alert.setGraphic(null);
        ((Button) alert.getDialogPane().lookupButton(restore)).getStyleClass().add(Styles.DANGER);
        return show(alert, restore, cancel);
    }

    private ButtonType cancelButton() {
        return new ButtonType(messages.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    private boolean show(Alert alert, ButtonType confirm, ButtonType cancel) {
        ((Button) alert.getDialogPane().lookupButton(confirm)).setDefaultButton(false);
        ((Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);

        themeManager.style(alert.getDialogPane());
        if (alert.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            stage.getIcons().setAll(AppIcons.all());
        }
        alert.setOnShowing(showing -> Motion.fadeIn(alert.getDialogPane().getScene().getWindow()));
        return alert.showAndWait().filter(confirm::equals).isPresent();
    }
}
