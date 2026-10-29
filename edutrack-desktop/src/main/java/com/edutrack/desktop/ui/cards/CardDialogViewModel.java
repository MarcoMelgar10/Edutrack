package com.edutrack.desktop.ui.cards;

import com.edutrack.application.goal.GoalService;
import com.edutrack.application.review.CardView;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.StepChoice;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import com.edutrack.domain.review.CardDetails;
import com.edutrack.domain.review.CardId;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * State of the Card dialog. Call {@link #create(GoalId, StepId, String)} for a new card of a goal (optionally
 * filed under a step and with the front already filled, e.g. from selected text) or {@link #edit(CardView)}
 * to change an existing one. Tags are typed as a comma-separated list.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CardDialogViewModel {

    private final ReviewService reviews;
    private final GoalService goals;
    private final Messages messages;

    private final StringProperty front = new SimpleStringProperty("");
    private final StringProperty back = new SimpleStringProperty("");
    private final StringProperty tags = new SimpleStringProperty("");
    private final BooleanProperty reversible = new SimpleBooleanProperty(false);
    private final ObjectProperty<StepChoice> step = new SimpleObjectProperty<>();
    private final ObservableList<StepChoice> steps = FXCollections.observableArrayList();
    private final StringProperty error = new SimpleStringProperty("");
    private final BooleanBinding valid;
    private GoalId goalId;
    private CardId editing;

    public CardDialogViewModel(ReviewService reviews, GoalService goals, Messages messages) {
        this.reviews = reviews;
        this.goals = goals;
        this.messages = messages;
        this.valid = Bindings.createBooleanBinding(() -> !isBlank(front.get()) && !isBlank(back.get()), front, back);
    }

    /** A new card for {@code goalId}; {@code stepId} and {@code frontText} may be {@code null}. */
    public void create(GoalId goalId, StepId stepId, String frontText) {
        this.goalId = goalId;
        this.editing = null;
        loadSteps(stepId);
        front.set(frontText == null ? "" : frontText);
    }

    public void edit(CardView card) {
        this.goalId = card.goalId();
        this.editing = card.id();
        loadSteps(card.stepId());
        front.set(card.front());
        back.set(card.back());
        tags.set(String.join(", ", card.tags()));
        reversible.set(card.reversible());
    }

    private void loadSteps(StepId selected) {
        List<StepChoice> choices = StepChoice.of(goals.getGoal(goalId), messages.get("cards.step.none"));
        steps.setAll(choices);
        step.set(StepChoice.find(choices, selected));
    }

    public String title() {
        return messages.get(editing == null ? "dialog.card.new" : "dialog.card.edit");
    }

    public String saveLabel() {
        return messages.get(editing == null ? "dialog.card.create" : "common.saveChanges");
    }

    /** Saves through the application service; domain validation errors propagate to the dialog. */
    public CardView submit() {
        error.set("");
        StepChoice chosen = step.get();
        CardDetails details = new CardDetails(chosen == null ? null : chosen.id(), front.get(), back.get(),
                parseTags(tags.get()), reversible.get());
        return editing == null ? reviews.createCard(goalId, details) : reviews.updateCard(editing, details);
    }

    /** "verbs, B2 ,, irregular" becomes {verbs, B2, irregular}; the card lower-cases and sorts them. */
    static Set<String> parseTags(String text) {
        Set<String> parsed = new LinkedHashSet<>();
        if (text != null) {
            for (String tag : text.split("[,;]")) {
                if (!tag.isBlank()) {
                    parsed.add(tag.strip());
                }
            }
        }
        return parsed;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    public StringProperty frontProperty() {
        return front;
    }

    public StringProperty backProperty() {
        return back;
    }

    public StringProperty tagsProperty() {
        return tags;
    }

    public BooleanProperty reversibleProperty() {
        return reversible;
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
