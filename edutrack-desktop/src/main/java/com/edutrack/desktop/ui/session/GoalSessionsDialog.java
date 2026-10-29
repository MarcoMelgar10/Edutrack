package com.edutrack.desktop.ui.session;

import com.edutrack.desktop.ui.common.AppIcons;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.desktop.ui.theme.ThemeManager;
import com.edutrack.domain.goal.GoalId;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

/** "View all sessions…" on the goal panel: every session logged on the goal, with edit and delete. */
@Component
public class GoalSessionsDialog {

    private final ViewLoader viewLoader;
    private final ThemeManager themeManager;
    private final Messages messages;

    public GoalSessionsDialog(ViewLoader viewLoader, ThemeManager themeManager, Messages messages) {
        this.viewLoader = viewLoader;
        this.themeManager = themeManager;
        this.messages = messages;
    }

    public void show(Window owner, GoalId goalId, String goalTitle) {
        ViewLoader.LoadedView<SessionListController> view = viewLoader.load(SessionListController.class);
        view.controller().showGoal(goalId);

        Dialog<Void> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle(messages.get("session.all.title"));
        dialog.setHeaderText(messages.get("session.all.header", goalTitle));
        DialogPane pane = dialog.getDialogPane();
        pane.setContent(view.root());
        pane.getStyleClass().add("form-dialog");
        pane.setPrefSize(620, 480);
        pane.getButtonTypes().setAll(new ButtonType(messages.get("common.close"), ButtonType.CLOSE.getButtonData()));

        themeManager.style(pane);
        if (pane.getScene().getWindow() instanceof Stage stage) {
            stage.getIcons().setAll(AppIcons.all());
        }
        dialog.setOnShowing(showing -> Motion.fadeIn(pane.getScene().getWindow()));
        dialog.setOnHidden(hidden -> view.controller().clear());
        dialog.showAndWait();
    }
}
