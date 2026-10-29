package com.edutrack.desktop.ui.cards;

import com.edutrack.application.goal.GoalService;
import com.edutrack.application.review.ImportResult;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.StepChoice;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * State of the Import dialog: text pasted or read from a .txt / .tsv / .csv file, one card per line, filed
 * under an optional step. {@link CardImportReport} words the outcome.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CardImportViewModel {

    private static final char BYTE_ORDER_MARK = 0xFEFF;

    private final ReviewService reviews;
    private final GoalService goals;
    private final Messages messages;

    private final StringProperty text = new SimpleStringProperty("");
    private final ObjectProperty<StepChoice> step = new SimpleObjectProperty<>();
    private final ObservableList<StepChoice> steps = FXCollections.observableArrayList();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private GoalId goalId;

    public CardImportViewModel(ReviewService reviews, GoalService goals, Messages messages) {
        this.reviews = reviews;
        this.goals = goals;
        this.messages = messages;
        this.valid = Bindings.createBooleanBinding(() -> !text.get().isBlank(), text);
    }

    /** Imports into {@code goalId}, filed under {@code stepId} when it is given. */
    public void create(GoalId goalId, StepId stepId) {
        this.goalId = goalId;
        List<StepChoice> choices = StepChoice.of(goals.getGoal(goalId), messages.get("cards.step.none"));
        steps.setAll(choices);
        step.set(StepChoice.find(choices, stepId));
    }

    public String title() {
        return messages.get("dialog.cardImport.title");
    }

    public String saveLabel() {
        return messages.get("dialog.cardImport.import");
    }

    /** Creates the cards; lines that cannot be cards come back in the result instead of failing. */
    public ImportResult submit() {
        error.set("");
        StepChoice chosen = step.get();
        return reviews.importDelimited(goalId, chosen == null ? null : chosen.id(), text.get());
    }

    /** Puts the content of a text file in the text area; a file that cannot be read sets the error. */
    public void load(Path file) {
        error.set("");
        try {
            text.set(read(file));
        } catch (IOException unreadable) {
            error.set(messages.get("cards.import.unreadable", file.getFileName().toString()));
        }
    }

    /** UTF-8 first (Anki's export); a file in an older Windows encoding is read as Latin-1 instead of failing. */
    private static String read(Path file) throws IOException {
        String content;
        try {
            content = Files.readString(file, StandardCharsets.UTF_8);
        } catch (MalformedInputException notUtf8) {
            content = Files.readString(file, StandardCharsets.ISO_8859_1);
        }
        return !content.isEmpty() && content.charAt(0) == BYTE_ORDER_MARK ? content.substring(1) : content;
    }

    public StringProperty textProperty() {
        return text;
    }

    public ObjectProperty<StepChoice> stepProperty() {
        return step;
    }

    public ObservableList<StepChoice> steps() {
        return steps;
    }

    public StringProperty errorProperty() {
        return error;
    }

    public BooleanBinding validProperty() {
        return valid;
    }
}
