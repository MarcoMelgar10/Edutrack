package com.edutrack.desktop.ui.session;

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

import java.util.Optional;

/**
 * The questions the study timer asks: stop the running session first, discard a short or unwanted
 * one, and what to do with a session found saved at start-up. Same look as {@code Confirmations}:
 * the safe answer is the default button, so Enter never discards anything.
 */
@Component
public class SessionPrompts {

    /** What to do with a session found saved when the app starts. */
    public enum RecoveryChoice { SAVE, RESUME, DISCARD }

    private final ThemeManager themeManager;
    private final Messages messages;

    public SessionPrompts(ThemeManager themeManager, Messages messages) {
        this.themeManager = themeManager;
        this.messages = messages;
    }

    /** "Stop and save the current session first?" — asked when a session starts while another is timed. */
    public boolean confirmStopCurrent(Window owner, String currentTitle) {
        ButtonType stop = new ButtonType(messages.get("session.stopAndSave"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = cancel();
        return ask(owner, messages.get("session.stopCurrent.title"), messages.get("session.stopCurrent.header"),
                messages.get("session.stopCurrent.content", currentTitle), cancel, stop, cancel)
                .filter(stop::equals).isPresent();
    }

    /** "Discard this short session?" — a session under one minute is not saved. */
    public boolean confirmDiscardShort(Window owner) {
        ButtonType discard = new ButtonType(messages.get("session.discard"), ButtonBar.ButtonData.OK_DONE);
        ButtonType keep = new ButtonType(messages.get("session.keep"), ButtonBar.ButtonData.CANCEL_CLOSE);
        return ask(owner, messages.get("session.discardShort.title"), messages.get("session.discardShort.header"),
                messages.get("session.discardShort.content"), keep, discard, keep)
                .filter(discard::equals).isPresent();
    }

    /** "Discard this session?" — asked before throwing away time that was being counted. */
    public boolean confirmDiscard(Window owner, String elapsed) {
        ButtonType discard = new ButtonType(messages.get("session.discard"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = cancel();
        return ask(owner, messages.get("session.discardRunning.title"), messages.get("session.discardRunning.header"),
                messages.get("session.discardRunning.content", elapsed), cancel, discard, cancel)
                .filter(discard::equals).isPresent();
    }

    /**
     * "A session for «Goal · Step» was running since 18:05 (42 min). Save it?" — Resume is the default and the
     * button Esc or the window's close box answers with, since it is the choice that loses nothing.
     */
    public RecoveryChoice recovery(Window owner, String title, String since, int minutes) {
        ButtonType save = new ButtonType(messages.get("session.recovery.save"), ButtonBar.ButtonData.OK_DONE);
        ButtonType resume = new ButtonType(messages.get("session.recovery.resume"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType discard = new ButtonType(messages.get("session.discard"), ButtonBar.ButtonData.OTHER);
        Optional<ButtonType> answer = ask(owner, messages.get("session.recovery.title"),
                messages.get("session.recovery.header", title, since, String.valueOf(minutes)),
                messages.get("session.recovery.content"), resume, save, resume, discard);
        if (answer.filter(save::equals).isPresent()) {
            return RecoveryChoice.SAVE;
        }
        return answer.filter(discard::equals).isPresent() ? RecoveryChoice.DISCARD : RecoveryChoice.RESUME;
    }

    private ButtonType cancel() {
        return new ButtonType(messages.get("common.cancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    /** {@code safe} is the default button (Enter); the first of {@code buttons} gets the accent. */
    private Optional<ButtonType> ask(Window owner, String title, String header, String content, ButtonType safe,
                                     ButtonType... buttons) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "", buttons);
        alert.initOwner(owner);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.setGraphic(null);

        for (ButtonType type : buttons) {
            ((Button) alert.getDialogPane().lookupButton(type)).setDefaultButton(false);
        }
        ((Button) alert.getDialogPane().lookupButton(buttons[0])).getStyleClass().add(Styles.ACCENT);
        ((Button) alert.getDialogPane().lookupButton(safe)).setDefaultButton(true);

        themeManager.style(alert.getDialogPane());
        if (alert.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            stage.getIcons().setAll(AppIcons.all());
        }
        alert.setOnShowing(showing -> Motion.fadeIn(alert.getDialogPane().getScene().getWindow()));
        return alert.showAndWait();
    }
}
