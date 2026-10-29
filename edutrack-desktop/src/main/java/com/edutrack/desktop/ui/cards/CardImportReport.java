package com.edutrack.desktop.ui.cards;

import com.edutrack.application.review.ImportResult;
import com.edutrack.desktop.ui.common.AppIcons;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.desktop.ui.theme.ThemeManager;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tells the learner how an import went: "Imported 120 cards · 2 lines skipped", followed by the lines that
 * could not become cards and why.
 */
@Component
public class CardImportReport {

    /** The service reports lines it cannot use as "line N: needs a front and a back..." or "line N: (rule)". */
    private static final Pattern SKIPPED_LINE = Pattern.compile("line (\\d+): (.*)", Pattern.DOTALL);
    private static final String NEEDS_BOTH_SIDES = "needs a front and a back";

    private final ThemeManager themeManager;
    private final Messages messages;

    public CardImportReport(ThemeManager themeManager, Messages messages) {
        this.themeManager = themeManager;
        this.messages = messages;
    }

    /** "Imported 120 cards" or "Imported 120 cards · 2 lines skipped". */
    public String headline(ImportResult result) {
        String imported = messages.plural("cards.import.imported", result.imported());
        return result.skipped().isEmpty()
                ? imported
                : imported + " · " + messages.plural("cards.import.skipped", result.skipped().size());
    }

    /** One entry per skipped line of the text, in the interface language. */
    public List<String> skippedLines(ImportResult result) {
        return result.skipped().stream().map(this::describe).toList();
    }

    private String describe(String skipped) {
        Matcher matcher = SKIPPED_LINE.matcher(skipped);
        if (!matcher.matches()) {
            return skipped;
        }
        String number = matcher.group(1);
        String reason = matcher.group(2);
        return reason.startsWith(NEEDS_BOTH_SIDES)
                ? messages.get("cards.import.line.needsBothSides", number)
                : messages.get("cards.import.line.reason", number, reason);
    }

    /** Shows the outcome in a message window over {@code owner}. */
    public void show(Window owner, ImportResult result) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, "", ButtonType.OK);
        alert.initOwner(owner);
        alert.setTitle(messages.get("dialog.cardImport.result"));
        alert.setHeaderText(headline(result));
        alert.setGraphic(null);
        if (result.skipped().isEmpty()) {
            alert.setContentText(null);
        } else {
            TextArea lines = new TextArea(String.join("\n", skippedLines(result)));
            lines.setEditable(false);
            lines.setWrapText(true);
            lines.setPrefRowCount(Math.min(8, result.skipped().size() + 1));
            lines.getStyleClass().add("monospace");
            alert.getDialogPane().setContent(lines);
        }
        themeManager.style(alert.getDialogPane());
        if (alert.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            stage.getIcons().setAll(AppIcons.all());
        }
        alert.setOnShowing(showing -> Motion.fadeIn(alert.getDialogPane().getScene().getWindow()));
        alert.showAndWait();
    }
}
