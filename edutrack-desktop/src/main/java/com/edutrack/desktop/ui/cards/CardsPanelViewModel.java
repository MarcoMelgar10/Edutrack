package com.edutrack.desktop.ui.cards;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.review.CardView;
import com.edutrack.application.review.ReviewService;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * The cards of one roadmap step (the Cards tab of the step page) or of a whole goal (the "Manage cards"
 * window of the goal panel). Reloading is the view's job: it calls {@link #refresh()} when its data may
 * have changed.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CardsPanelViewModel {

    private final ReviewService reviews;
    private final DataChanges dataChanges;
    private final Clock clock;
    private final Messages messages;

    private final ObservableList<CardView> cards = FXCollections.observableArrayList();
    private final ObservableList<CardView> cardsReadOnly = FXCollections.unmodifiableObservableList(cards);
    private final ReadOnlyStringWrapper tabText = new ReadOnlyStringWrapper("");
    private GoalId goalId;
    private StepId stepId;

    public CardsPanelViewModel(ReviewService reviews, DataChanges dataChanges, Clock clock, Messages messages) {
        this.reviews = reviews;
        this.dataChanges = dataChanges;
        this.clock = clock;
        this.messages = messages;
        tabText.set(messages.get("cards.tab"));
    }

    /** Lists every card of the goal. */
    public void showGoal(GoalId goalId) {
        show(goalId, null);
    }

    /** Lists the cards filed under one step of the goal; new cards start in that step. */
    public void showStep(GoalId goalId, StepId stepId) {
        show(goalId, stepId);
    }

    private void show(GoalId goal, StepId step) {
        this.goalId = goal;
        this.stepId = step;
        refresh();
    }

    /** Stops listing (the step page was closed). */
    public void clear() {
        goalId = null;
        stepId = null;
        refresh();
    }

    public void refresh() {
        List<CardView> loaded = goalId == null ? List.of()
                : stepId == null ? reviews.forGoal(goalId) : reviews.forStep(stepId);
        cards.setAll(loaded);
        tabText.set(loaded.isEmpty() ? messages.get("cards.tab") : messages.get("cards.tab.count", loaded.size()));
    }

    public void delete(CardView card) {
        try {
            reviews.deleteCard(card.id());
        } catch (NotFoundException alreadyGone) {
            // deleted elsewhere: the list below is refreshed either way
        }
        refresh();
        dataChanges.markChanged();
    }

    /** How the row of a card says when it is next due. */
    public String dueText(CardView card) {
        return CardFormats.due(card, LocalDate.now(clock));
    }

    public GoalId goalId() {
        return goalId;
    }

    public StepId stepId() {
        return stepId;
    }

    public boolean hasTarget() {
        return goalId != null;
    }

    public ObservableList<CardView> cards() {
        return cardsReadOnly;
    }

    /** "Cards" or "Cards (12)" for a tab header. */
    public ReadOnlyStringProperty tabTextProperty() {
        return tabText.getReadOnlyProperty();
    }
}
