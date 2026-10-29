package com.edutrack.desktop.ui.cards;

import com.edutrack.application.review.CardView;
import com.edutrack.application.review.ImportResult;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.stage.Window;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * The entry points other screens use to work with flashcards: open the card dialog for a new or an existing
 * card, or the import dialog. Each returns what was saved, or empty when the learner cancelled.
 *
 * <p>A screen that lets the learner pick text (a notes editor, a reading view) calls
 * {@link #openNewCard(Window, GoalId, StepId, String)} with the selection as the front of the card.
 */
@Component
public class Cards {

    private final FormDialogs dialogs;
    private final CardImportReport importReport;

    public Cards(FormDialogs dialogs, CardImportReport importReport) {
        this.dialogs = dialogs;
        this.importReport = importReport;
    }

    /**
     * Opens the Card dialog for a new card of {@code goalId}.
     *
     * @param stepId optional: the roadmap step the card is filed under
     * @param front  optional: text for the front of the card (e.g. the selected text); the cursor then
     *               starts in the back
     */
    public Optional<CardView> openNewCard(Window owner, GoalId goalId, StepId stepId, String front) {
        return dialogs.show(owner, CardDialogController.class, form -> form.create(goalId, stepId, front));
    }

    /** Opens the Card dialog to change an existing card. */
    public Optional<CardView> openEditCard(Window owner, CardView card) {
        return dialogs.show(owner, CardDialogController.class, form -> form.edit(card));
    }

    /**
     * Opens the Import dialog (text pasted or read from a file, one card per line) and, once the cards are
     * created, tells the learner how many were imported and which lines were skipped.
     */
    public Optional<ImportResult> openImport(Window owner, GoalId goalId, StepId stepId) {
        Optional<ImportResult> result = dialogs.show(owner, CardImportController.class,
                form -> form.create(goalId, stepId));
        result.ifPresent(imported -> importReport.show(owner, imported));
        return result;
    }
}
