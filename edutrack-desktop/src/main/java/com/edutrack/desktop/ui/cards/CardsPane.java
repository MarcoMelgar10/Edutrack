package com.edutrack.desktop.ui.cards;

import atlantafx.base.theme.Styles;
import com.edutrack.application.review.CardView;
import com.edutrack.application.review.ReviewScope;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Subscription;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * The list of flashcards of a goal or of one step, with the buttons to add, import and edit them and, for a
 * whole goal, to review just that goal. It is the Cards tab of both the goal page and the step page.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CardsPane {

    private final CardsPanelViewModel viewModel;
    private final Cards cards;
    private final Confirmations confirmations;
    private final Navigator navigator;
    private final DataChanges dataChanges;
    private final Messages messages;

    private final VBox root = new VBox(10);
    private final ListView<CardView> list = new ListView<>();
    private final Button reviewButton;
    private Subscription following = Subscription.EMPTY;

    public CardsPane(CardsPanelViewModel viewModel, Cards cards, Confirmations confirmations, Navigator navigator,
                     DataChanges dataChanges, Messages messages) {
        this.viewModel = viewModel;
        this.cards = cards;
        this.confirmations = confirmations;
        this.navigator = navigator;
        this.dataChanges = dataChanges;
        this.messages = messages;

        Button add = new Button(messages.get("cards.add"));
        add.getStyleClass().add(Styles.ACCENT);
        add.setGraphic(new FontIcon(Feather.PLUS));
        add.setOnAction(click -> cards.openNewCard(window(), viewModel.goalId(), viewModel.stepId(), null));
        Button importCards = new Button(messages.get("cards.import.button"));
        importCards.setGraphic(new FontIcon(Feather.UPLOAD));
        importCards.setOnAction(click -> cards.openImport(window(), viewModel.goalId(), viewModel.stepId()));
        reviewButton = new Button(messages.get("cards.reviewGoal"));
        reviewButton.setGraphic(new FontIcon(Feather.LAYERS));
        reviewButton.setOnAction(click -> navigator.openReview(ReviewScope.ofGoal(viewModel.goalId())));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox toolbar = new HBox(8, add, importCards, spacer, reviewButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        Label placeholder = new Label(messages.get("cards.empty"));
        placeholder.getStyleClass().add("text-caption");
        placeholder.setWrapText(true);
        list.setPlaceholder(placeholder);
        list.getStyleClass().add("record-list");
        list.setItems(viewModel.cards());
        list.setCellFactory(view -> new CardCell());
        VBox.setVgrow(list, Priority.ALWAYS);

        root.getChildren().addAll(toolbar, list);
        root.setPadding(new Insets(12, 4, 16, 0));
        root.getStyleClass().add("goal-tab-body");
    }

    // ---- showing ----

    /** Every card of the goal, with a button to review just them. */
    public void showGoal(GoalId goalId) {
        reviewButton.setVisible(true);
        reviewButton.setManaged(true);
        follow();
        viewModel.showGoal(goalId);
    }

    /** The cards filed under one step. */
    public void showStep(GoalId goalId, StepId stepId) {
        reviewButton.setVisible(false);
        reviewButton.setManaged(false);
        follow();
        viewModel.showStep(goalId, stepId);
    }

    public void clear() {
        following.unsubscribe();
        following = Subscription.EMPTY;
        viewModel.clear();
    }

    private void follow() {
        following.unsubscribe();
        following = dataChanges.subscribe(viewModel::refresh);
    }

    public Node content() {
        return root;
    }

    public ReadOnlyStringProperty tabTextProperty() {
        return viewModel.tabTextProperty();
    }

    private Window window() {
        return root.getScene().getWindow();
    }

    // ---- rows ----

    private final class CardCell extends ListCell<CardView> {

        @Override
        protected void updateItem(CardView card, boolean empty) {
            super.updateItem(card, empty);
            if (empty || card == null) {
                setGraphic(null);
                setText(null);
                return;
            }
            Label front = new Label(CardFormats.firstLine(card.front()));
            front.getStyleClass().add("routine-title");
            Label back = new Label(CardFormats.firstLine(card.back()));
            back.getStyleClass().add("text-caption");
            VBox text = new VBox(2, front, back);
            HBox.setHgrow(text, Priority.ALWAYS);

            HBox row = new HBox(10, text);
            row.setAlignment(Pos.CENTER_LEFT);
            if (card.reversible()) {
                FontIcon both = new FontIcon(Feather.REPEAT);
                both.getStyleClass().add("routine-mark");
                Tooltip.install(both, new Tooltip(messages.get("cards.reversible.tooltip")));
                row.getChildren().add(both);
            }
            if (!card.tags().isEmpty()) {
                Label tags = new Label(String.join(" · ", card.tags()));
                tags.getStyleClass().add("text-caption");
                row.getChildren().add(tags);
            }
            Label due = new Label(viewModel.dueText(card));
            due.getStyleClass().add("text-caption");
            due.setMinWidth(96);
            due.setAlignment(Pos.CENTER_RIGHT);
            Button edit = icon(Feather.EDIT_2, messages.get("common.edit"));
            edit.setOnAction(click -> cards.openEditCard(window(), card));
            Button delete = icon(Feather.TRASH_2, messages.get("common.delete"));
            delete.setOnAction(click -> {
                if (confirmations.confirmDelete(window(), "card", CardFormats.firstLine(card.front()), null)) {
                    viewModel.delete(card);
                }
            });
            row.getChildren().addAll(due, edit, delete);
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
