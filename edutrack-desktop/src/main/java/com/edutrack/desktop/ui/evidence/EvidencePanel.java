package com.edutrack.desktop.ui.evidence;

import atlantafx.base.theme.Styles;
import com.edutrack.application.evidence.EvidenceView;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.goal.GoalPanel;
import com.edutrack.domain.evidence.EvidenceKind;
import javafx.application.HostServices;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

import java.nio.file.Path;

/**
 * The Evidence tab of a goal: the portfolio of what shows the learning (projects, recordings, essays,
 * certificates), each with its link and its file one click away.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Order(40)
public class EvidencePanel implements GoalPanel {

    private final EvidencePanelViewModel viewModel;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final HostServices hostServices;
    private final Messages messages;

    private final ScrollPane content = new ScrollPane();
    private final VBox list = new VBox();

    public EvidencePanel(EvidencePanelViewModel viewModel, FormDialogs dialogs, Confirmations confirmations,
                         HostServices hostServices, Messages messages) {
        this.viewModel = viewModel;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.hostServices = hostServices;
        this.messages = messages;
        build();
    }

    private void build() {
        Label title = new Label(messages.get("evidence.title"));
        title.getStyleClass().add("section-label");
        Button add = new Button(messages.get("evidence.add"));
        add.setGraphic(new FontIcon(Feather.PLUS));
        add.getStyleClass().add(Styles.ACCENT);
        add.setOnAction(click -> dialogs.show(window(), EvidenceDialogController.class,
                form -> form.create(viewModel.goal().id(), null)));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(title, spacer, add);
        header.setAlignment(Pos.CENTER_LEFT);

        Label hint = new Label(messages.get("evidence.hint"));
        hint.getStyleClass().add("text-caption");
        hint.setWrapText(true);

        Label empty = new Label(messages.get("evidence.empty"));
        empty.getStyleClass().add("text-caption");
        empty.setWrapText(true);

        list.getStyleClass().add("skill-list");
        viewModel.items().addListener((ListChangeListener<EvidenceView>) change -> render(empty));

        VBox body = new VBox(10, header, hint, list);
        body.getStyleClass().add("goal-tab-body");
        body.setPadding(new Insets(12, 4, 24, 0));
        content.setContent(body);
        content.setFitToWidth(true);
        content.getStyleClass().add("goal-tab-scroll");
        render(empty);
    }

    private void render(Label empty) {
        list.getChildren().clear();
        if (viewModel.items().isEmpty()) {
            list.getChildren().add(empty);
            return;
        }
        viewModel.items().forEach(evidence -> list.getChildren().add(row(evidence)));
    }

    private Node row(EvidenceView evidence) {
        FontIcon kind = new FontIcon(icon(evidence.kind()));
        kind.getStyleClass().add("routine-mark");
        Tooltip.install(kind, new Tooltip(messages.get("evidence.kind." + evidence.kind().name())));

        Label name = new Label(evidence.title());
        name.getStyleClass().add("routine-title");
        Label caption = new Label(viewModel.captionOf(evidence));
        caption.getStyleClass().add("text-caption");
        VBox text = new VBox(2, name, caption);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox line = new HBox(12, kind, text);
        line.setAlignment(Pos.CENTER_LEFT);
        line.getStyleClass().add("routine-row");
        if (evidence.url() != null) {
            Button open = button(Feather.EXTERNAL_LINK, messages.get("evidence.openLink"));
            open.setOnAction(click -> hostServices.showDocument(evidence.url()));
            line.getChildren().add(open);
        }
        Path file = evidence.attachment() == null ? null : viewModel.fileOf(evidence);
        if (file != null) {
            Button open = button(Feather.FILE, messages.get("evidence.openFile", evidence.attachment().name()));
            open.setOnAction(click -> hostServices.showDocument(file.toUri().toString()));
            line.getChildren().add(open);
        }
        Button more = button(Feather.MORE_HORIZONTAL, messages.get("practice.routine.more"));
        ContextMenu menu = menu(evidence);
        more.setOnAction(click -> menu.show(more, javafx.geometry.Side.BOTTOM, 0, 0));
        line.getChildren().add(more);
        line.setOnContextMenuRequested(event -> menu.show(line, event.getScreenX(), event.getScreenY()));
        return line;
    }

    private ContextMenu menu(EvidenceView evidence) {
        MenuItem edit = new MenuItem(messages.get("common.edit"));
        edit.setOnAction(click -> dialogs.show(window(), EvidenceDialogController.class, form -> form.edit(evidence)));
        MenuItem delete = new MenuItem(messages.get("common.delete"));
        delete.setOnAction(click -> {
            if (confirmations.confirmDelete(window(), "evidence", evidence.title(),
                    evidence.attachment() == null ? null : messages.get("evidence.deleteNote"))) {
                viewModel.delete(evidence.id());
            }
        });
        return new ContextMenu(edit, delete);
    }

    static Feather icon(EvidenceKind kind) {
        return switch (kind) {
            case PROJECT -> Feather.FOLDER;
            case RECORDING -> Feather.MIC;
            case ESSAY -> Feather.FILE_TEXT;
            case CERTIFICATE -> Feather.AWARD;
            case PRESENTATION -> Feather.MONITOR;
            case OTHER -> Feather.PAPERCLIP;
        };
    }

    private static Button button(Feather icon, String tip) {
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
        return viewModel.tabTextProperty();
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
