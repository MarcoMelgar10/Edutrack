package com.edutrack.desktop.ui.skills;

import com.edutrack.application.goal.GoalDetails;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.goal.GoalPanel;
import com.edutrack.domain.goal.MoveDirection;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The Skills tab of a goal: competencies with a level bar against their target, and the measures tracked
 * with numbers (an exam band, a tempo) with their latest value and trend. The goal's percentage is not
 * affected: it comes from the roadmap steps alone.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Order(20)
public class SkillsPanel implements GoalPanel {

    private final SkillsViewModel viewModel;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final Messages messages;

    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper();
    private final ScrollPane content = new ScrollPane();
    private final VBox competencyBox = new VBox();
    private final VBox measureBox = new VBox();

    public SkillsPanel(SkillsViewModel viewModel, FormDialogs dialogs, Confirmations confirmations, Messages messages) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.messages = messages;
        title.set(messages.get("goal.tab.skills"));
        build();
    }

    private void build() {
        Label competenciesTitle = sectionLabel(messages.get("skills.competencies.title"));
        Button addCompetency = addButton(messages.get("skills.competencies.add"),
                () -> dialogs.show(window(), CompetencyDialogController.class, form -> form.create(viewModel.goalId())));
        Label competenciesHint = hint(messages.get("skills.competencies.hint"));

        Label measuresTitle = sectionLabel(messages.get("skills.measures.title"));
        Button addMeasure = addButton(messages.get("skills.measures.add"),
                () -> dialogs.show(window(), MeasureDialogController.class, form -> form.create(viewModel.goalId())));
        Label measuresHint = hint(messages.get("skills.measures.hint"));

        competencyBox.getStyleClass().add("skill-list");
        measureBox.getStyleClass().add("skill-list");
        viewModel.competencies().addListener((ListChangeListener<SkillsViewModel.CompetencyRow>) change -> renderCompetencies());
        viewModel.measures().addListener((ListChangeListener<SkillsViewModel.MeasureRow>) change -> renderMeasures());

        VBox body = new VBox(10, header(competenciesTitle, addCompetency), competenciesHint, competencyBox,
                spaced(header(measuresTitle, addMeasure)), measuresHint, measureBox);
        body.getStyleClass().add("goal-tab-body");
        body.setPadding(new Insets(12, 4, 24, 0));
        content.setContent(body);
        content.setFitToWidth(true);
        content.getStyleClass().add("goal-tab-scroll");
    }

    // ---- competencies ----

    private void renderCompetencies() {
        competencyBox.getChildren().clear();
        for (SkillsViewModel.CompetencyRow row : viewModel.competencies()) {
            competencyBox.getChildren().add(competencyNode(row));
        }
    }

    private Node competencyNode(SkillsViewModel.CompetencyRow row) {
        var competency = row.competency();
        Label name = new Label(competency.name());
        name.getStyleClass().add("routine-title");
        Label level = new Label(row.levelText());
        level.getStyleClass().add("text-caption");
        Label target = new Label(row.targetText() + " · " + row.stepsText());
        target.getStyleClass().add("text-caption");
        VBox text = new VBox(2, name, level, target);
        HBox.setHgrow(text, Priority.ALWAYS);

        LevelBar bar = new LevelBar(competency.level(), competency.targetLevel(),
                next -> viewModel.setLevel(competency.id(), next), messages.get("skills.levelBar", competency.name(),
                row.levelText()));
        FontIcon met = new FontIcon(Feather.CHECK_CIRCLE);
        met.getStyleClass().add("skill-met");
        met.setVisible(competency.meetsTarget());
        met.setManaged(competency.meetsTarget());

        Button more = iconButton(Feather.MORE_HORIZONTAL, messages.get("practice.routine.more"));
        ContextMenu menu = competencyMenu(row);
        more.setOnAction(click -> menu.show(more, Side.BOTTOM, 0, 0));

        HBox line = new HBox(14, text, bar, met, more);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("routine-row");
        line.setOnContextMenuRequested(event -> menu.show(line, event.getScreenX(), event.getScreenY()));
        return line;
    }

    private ContextMenu competencyMenu(SkillsViewModel.CompetencyRow row) {
        var competency = row.competency();
        MenuItem edit = new MenuItem(messages.get("common.edit"));
        edit.setOnAction(click -> dialogs.show(window(), CompetencyDialogController.class, form -> form.edit(competency)));
        MenuItem link = new MenuItem(messages.get("skills.linkSteps"));
        link.setOnAction(click -> dialogs.show(window(), StepLinksDialogController.class, form -> form.show(competency)));
        MenuItem up = new MenuItem(messages.get("common.moveUp"));
        up.setOnAction(click -> viewModel.moveCompetency(competency.id(), MoveDirection.UP));
        MenuItem down = new MenuItem(messages.get("common.moveDown"));
        down.setOnAction(click -> viewModel.moveCompetency(competency.id(), MoveDirection.DOWN));
        MenuItem delete = new MenuItem(messages.get("common.delete"));
        delete.setOnAction(click -> {
            if (confirmations.confirmDelete(window(), "competency", competency.name(), null)) {
                viewModel.deleteCompetency(competency.id());
            }
        });
        return new ContextMenu(edit, link, up, down, delete);
    }

    // ---- measures ----

    private void renderMeasures() {
        measureBox.getChildren().clear();
        for (SkillsViewModel.MeasureRow row : viewModel.measures()) {
            measureBox.getChildren().add(measureNode(row));
        }
    }

    private Node measureNode(SkillsViewModel.MeasureRow row) {
        var measure = row.measure();
        Label name = new Label(measure.name());
        name.getStyleClass().add("routine-title");
        String detail = String.join(" · ", java.util.stream.Stream.of(row.targetText(), row.bestText(), row.resultsText())
                .filter(part -> !part.isEmpty()).toList());
        Label caption = new Label(detail);
        caption.getStyleClass().add("text-caption");
        VBox text = new VBox(2, name, caption);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label latest = new Label(row.latestText());
        latest.getStyleClass().add("measure-latest");
        FontIcon met = new FontIcon(Feather.CHECK_CIRCLE);
        met.getStyleClass().add("skill-met");
        met.setVisible(measure.meetsTarget());
        met.setManaged(measure.meetsTarget());

        Button more = iconButton(Feather.MORE_HORIZONTAL, messages.get("practice.routine.more"));
        ContextMenu menu = measureMenu(row);
        more.setOnAction(click -> menu.show(more, Side.BOTTOM, 0, 0));

        HBox line = new HBox(14, text, new Sparkline(row.trend()), latest, met, more);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("routine-row");
        line.setOnContextMenuRequested(event -> menu.show(line, event.getScreenX(), event.getScreenY()));
        return line;
    }

    private ContextMenu measureMenu(SkillsViewModel.MeasureRow row) {
        var measure = row.measure();
        MenuItem edit = new MenuItem(messages.get("common.edit"));
        edit.setOnAction(click -> dialogs.show(window(), MeasureDialogController.class, form -> form.edit(measure)));
        MenuItem delete = new MenuItem(messages.get("common.delete"));
        delete.setOnAction(click -> {
            if (confirmations.confirmDelete(window(), "measure", measure.name(), messages.get("skills.measure.deleteNote"))) {
                viewModel.deleteMeasure(measure.id());
            }
        });
        return new ContextMenu(edit, delete);
    }

    // ---- small helpers ----

    private static Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-label");
        return label;
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("text-caption");
        label.setWrapText(true);
        return label;
    }

    private static HBox header(Label label, Button add) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox box = new HBox(label, spacer, add);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private static Node spaced(Node node) {
        VBox.setMargin(node, new Insets(14, 0, 0, 0));
        return node;
    }

    private static Button addButton(String text, Runnable action) {
        Button button = new Button(text);
        button.setGraphic(new FontIcon(Feather.PLUS));
        button.setOnAction(click -> action.run());
        return button;
    }

    private static Button iconButton(Feather icon, String tip) {
        Button button = new Button();
        button.setGraphic(new FontIcon(icon));
        button.getStyleClass().add("icon-button");
        button.setTooltip(new Tooltip(tip));
        button.setAccessibleText(tip);
        return button;
    }

    private Window window() {
        return content.getScene().getWindow();
    }

    // ---- GoalPanel ----

    @Override
    public ObservableValue<String> title() {
        return title.getReadOnlyProperty();
    }

    @Override
    public Node content() {
        return content;
    }

    @Override
    public void show(GoalDetails goal) {
        viewModel.show(goal);
    }

    @Override
    public void clear() {
        viewModel.clear();
    }
}
