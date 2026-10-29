package com.edutrack.desktop.ui.cards;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.desktop.ui.goal.GoalPanel;
import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** The Cards tab of a goal: every flashcard of the goal, with add, import and "review this goal". */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Order(30)
public class CardsPanel implements GoalPanel {

    private final CardsPane pane;

    public CardsPanel(CardsPane pane) {
        this.pane = pane;
    }

    @Override
    public ObservableValue<String> title() {
        return pane.tabTextProperty();
    }

    @Override
    public Node content() {
        return pane.content();
    }

    @Override
    public void show(GoalDetails goal) {
        pane.showGoal(goal.id());
    }

    @Override
    public void clear() {
        pane.clear();
    }
}
