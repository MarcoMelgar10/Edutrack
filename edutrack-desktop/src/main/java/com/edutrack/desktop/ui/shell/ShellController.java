package com.edutrack.desktop.ui.shell;

import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.area.AreaTabController;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.common.Reorderable;
import com.edutrack.desktop.ui.common.ViewLoader;
import com.edutrack.desktop.ui.dialog.AreaDialogController;
import com.edutrack.desktop.ui.dialog.Confirmations;
import com.edutrack.desktop.ui.dialog.DeleteConsequences;
import com.edutrack.desktop.ui.dialog.FormDialogs;
import com.edutrack.desktop.ui.search.QuickSearch;
import com.edutrack.desktop.ui.session.SessionTimerBarController;
import com.edutrack.desktop.ui.theme.Motion;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.goal.StepId;
import javafx.application.Platform;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.collections.ListChangeListener;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.Subscription;
import org.kordamp.ikonli.feather.Feather;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Binds {@code ShellView.fxml} to {@link ShellViewModel}: the left rail (pages, areas, settings, theme)
 * and the center, which shows one page at a time. Fixed pages come from {@link ShellPages}; every area
 * gets its own page. Pages are created when first shown and then kept, so each area page keeps its
 * selection, and an area's page is disposed when the area is deleted.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ShellController implements Navigator.Target {

    /** What the window was showing: a fixed page, or (when {@code page} is {@code null}) an area page. */
    public record Location(ShellPage page, AreaId area) {
    }

    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("current");
    private static final PseudoClass HAS_BADGE = PseudoClass.getPseudoClass("has-badge");
    private static final ShellPage[] RAIL_PAGES =
            {ShellPage.TODAY, ShellPage.REVIEW, ShellPage.JOURNAL, ShellPage.INSIGHTS, ShellPage.SEARCH};
    private static final KeyCombination FOCUS_SHORTCUT =
            new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN);
    private static final KeyCombination LEAVE_FOCUS = new KeyCodeCombination(KeyCode.ESCAPE);
    private static final double RAIL_WIDTH = 240;
    private static final double RAIL_COMPACT_WIDTH = 52;
    private static final KeyCode[] DIGITS = {KeyCode.DIGIT1, KeyCode.DIGIT2, KeyCode.DIGIT3, KeyCode.DIGIT4, KeyCode.DIGIT5};

    private final ShellViewModel viewModel;
    private final ViewLoader viewLoader;
    private final ShellPages shellPages;
    private final FormDialogs dialogs;
    private final Confirmations confirmations;
    private final Navigator navigator;
    private final Messages messages;
    private final QuickSearch quickSearch;
    private final FocusMode focusMode;

    private final Map<ShellPage, NavItem> navItems = new EnumMap<>(ShellPage.class);
    private final Map<ShellPage, ViewLoader.LoadedView<?>> pages = new EnumMap<>(ShellPage.class);
    private final Map<AreaId, AreaPage> areaPages = new HashMap<>();
    private final Map<KeyCombination, Runnable> accelerators = new HashMap<>();

    @FXML
    private BorderPane root;
    @FXML
    private VBox rail;
    @FXML
    private Label appTitle;
    @FXML
    private Button sidebarToggle;
    @FXML
    private HBox areasHeader;
    @FXML
    private Button exitFocusButton;
    @FXML
    private VBox navBox;
    @FXML
    private Button newAreaButton;
    @FXML
    private ListView<AreaSummary> areaList;
    @FXML
    private HBox footer;
    @FXML
    private StackPane content;
    @FXML
    private StackPane dock;

    private ShellPage currentPage;
    private AreaId currentArea;
    private Node shown;
    private boolean syncingSelection;
    private Subscription sceneSubscription = Subscription.EMPTY;
    private Subscription windowSubscription = Subscription.EMPTY;
    private Subscription focusSubscription = Subscription.EMPTY;
    private Scene acceleratorScene;
    private SessionTimerBarController sessionBar;
    private Subscription focusModeSubscription = Subscription.EMPTY;
    private Timeline railAnimation;

    private record AreaPage(Node root, AreaTabController controller) {
    }

    public ShellController(ShellViewModel viewModel, ViewLoader viewLoader, ShellPages shellPages, FormDialogs dialogs,
                           Confirmations confirmations, Navigator navigator, Messages messages,
                           QuickSearch quickSearch, FocusMode focusMode) {
        this.viewModel = viewModel;
        this.viewLoader = viewLoader;
        this.shellPages = shellPages;
        this.dialogs = dialogs;
        this.confirmations = confirmations;
        this.navigator = navigator;
        this.messages = messages;
        this.quickSearch = quickSearch;
        this.focusMode = focusMode;
    }

    @FXML
    private void initialize() {
        for (ShellPage page : RAIL_PAGES) {
            NavItem item = new NavItem(page);
            navItems.put(page, item);
            navBox.getChildren().add(item.button);
        }
        NavItem settings = new NavItem(ShellPage.SETTINGS);
        navItems.put(ShellPage.SETTINGS, settings);
        HBox.setHgrow(settings.button, Priority.ALWAYS);
        footer.getChildren().addAll(settings.button, focusToggle(), themeToggle());

        newAreaButton.setGraphic(new FontIcon(Feather.PLUS));

        // The dock takes no space until something (the session bar) is placed in it.
        dock.visibleProperty().bind(Bindings.isNotEmpty(dock.getChildren()));
        dock.managedProperty().bind(dock.visibleProperty());
        ViewLoader.LoadedView<SessionTimerBarController> bar = viewLoader.load(SessionTimerBarController.class);
        sessionBar = bar.controller();
        sessionBar.dockInto(dock);

        initializeAreaList();
        initializeFocus();

        for (int index = 0; index < RAIL_PAGES.length; index++) {
            ShellPage page = RAIL_PAGES[index];
            accelerators.put(new KeyCodeCombination(DIGITS[index], KeyCombination.CONTROL_DOWN), () -> showPage(page));
        }
        accelerators.put(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN),
                () -> showPage(ShellPage.SETTINGS));
        accelerators.put(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN), quickSearch::toggle);
        accelerators.put(FOCUS_SHORTCUT, focusMode::toggleFocus);
        sceneSubscription = root.sceneProperty().subscribe(this::onScene);

        navigator.attach(this);
        viewModel.areas().addListener((ListChangeListener<AreaSummary>) change -> onAreasChanged());
        showPage(ShellPage.TODAY);
    }

    private Button themeToggle() {
        Button toggle = new Button();
        toggle.getStyleClass().add("icon-button");
        FontIcon icon = new FontIcon();
        Tooltip tooltip = new Tooltip();
        toggle.setGraphic(icon);
        toggle.setTooltip(tooltip);
        toggle.setAccessibleText(messages.get("shell.theme.toggle"));
        toggle.setOnAction(click -> viewModel.toggleTheme());
        // Moon offers the dark theme, sun offers the light one. Same glyph style in both themes.
        viewModel.darkModeProperty().subscribe(dark -> {
            icon.setIconCode(dark ? Feather.SUN : Feather.MOON);
            tooltip.setText(messages.get(dark ? "shell.theme.toLight" : "shell.theme.toDark"));
        });
        return toggle;
    }

    private Button focusToggle() {
        Button toggle = new Button();
        toggle.getStyleClass().add("icon-button");
        toggle.setGraphic(new FontIcon(Feather.MAXIMIZE));
        String label = messages.get("focus.enter");
        toggle.setTooltip(new Tooltip(label + " (Ctrl+Shift+F)"));
        toggle.setAccessibleText(label);
        toggle.setOnAction(click -> focusMode.toggleFocus());
        return toggle;
    }

    /** Wires the sidebar toggle, the compact rail and focus mode (everything hidden but "Exit focus"). */
    private void initializeFocus() {
        sidebarToggle.setGraphic(new FontIcon(Feather.MENU));
        focusMode.sidebarCollapsedProperty().subscribe(collapsed -> {
            String label = messages.get(collapsed ? "shell.sidebar.show" : "shell.sidebar.hide");
            sidebarToggle.setTooltip(new Tooltip(label));
            sidebarToggle.setAccessibleText(label);
        });

        exitFocusButton.setGraphic(new FontIcon(Feather.MINIMIZE));
        exitFocusButton.setText(messages.get("focus.exit"));
        exitFocusButton.setTooltip(new Tooltip(messages.get("focus.exit") + " (Esc, Ctrl+Shift+F)"));
        exitFocusButton.visibleProperty().bind(focusMode.focusProperty());
        exitFocusButton.managedProperty().bind(exitFocusButton.visibleProperty());

        applyRail(focusMode.sidebarCompact().get(), focusMode.isFocus(), false);
        focusModeSubscription = Subscription.combine(
                focusMode.sidebarCompact().subscribe(compact -> applyRail(compact, focusMode.isFocus(), true)),
                focusMode.focusProperty().subscribe(focus -> {
                    applyRail(focusMode.sidebarCompact().get(), focus, true);
                    syncLeaveFocus();
                }));
    }

    /** Esc leaves focus mode, but only while it is on, so it never competes with other uses of Esc. */
    private void syncLeaveFocus() {
        if (acceleratorScene == null) {
            return;
        }
        if (focusMode.isFocus()) {
            acceleratorScene.getAccelerators().put(LEAVE_FOCUS, focusMode::exitFocus);
        } else {
            acceleratorScene.getAccelerators().remove(LEAVE_FOCUS);
        }
    }

    /** Hides the rail (focus), folds it to its toggle (compact) or shows it whole, gliding the width. */
    private void applyRail(boolean compact, boolean focus, boolean animate) {
        if (railAnimation != null) {
            railAnimation.stop();
        }
        rail.setVisible(!focus);
        rail.setManaged(!focus);
        rail.getStyleClass().remove("compact");
        if (compact) {
            rail.getStyleClass().add("compact");
        }
        boolean full = !compact;
        for (Node part : List.of(navBox, areasHeader, areaList, footer, appTitle)) {
            part.setVisible(full);
            part.setManaged(full);
        }
        double target = compact ? RAIL_COMPACT_WIDTH : RAIL_WIDTH;
        if (!animate || focus || rail.getScene() == null) {
            setRailWidth(target);
            return;
        }
        double from = compact ? RAIL_WIDTH : RAIL_COMPACT_WIDTH;
        DoubleProperty width = new SimpleDoubleProperty(from);
        width.subscribe(value -> setRailWidth(value.doubleValue()));
        railAnimation = new Timeline(new KeyFrame(Motion.FADE, new KeyValue(width, target, Interpolator.EASE_BOTH)));
        railAnimation.setOnFinished(done -> setRailWidth(target));
        railAnimation.play();
        if (full) {
            Motion.fadeIn(navBox);
            Motion.fadeIn(areaList);
        }
    }

    private void setRailWidth(double width) {
        rail.setMinWidth(width);
        rail.setPrefWidth(width);
        rail.setMaxWidth(width);
    }

    @FXML
    private void toggleSidebar() {
        focusMode.toggleSidebar();
    }

    @FXML
    private void exitFocus() {
        focusMode.exitFocus();
    }

    private void initializeAreaList() {
        AreaActions actions = new AreaActions();
        // The areas are replaced with setAll on every refresh, which makes the list view move its selection.
        // This listener is registered BEFORE the list view takes the items (listeners run in registration
        // order) to mute the selection listener until the list view is done; the second one restores it.
        viewModel.areas().addListener((ListChangeListener<AreaSummary>) change -> syncingSelection = true);
        areaList.setItems(viewModel.areas());
        areaList.setCellFactory(list -> new AreaRailCell(actions));
        viewModel.areas().addListener((ListChangeListener<AreaSummary>) change -> {
            selectCurrentArea();
            syncingSelection = false;
        });
        areaList.getSelectionModel().selectedItemProperty().subscribe(selected -> {
            if (!syncingSelection && selected != null) {
                showArea(selected.id());
            }
        });
        Reorderable.installKeys(areaList, actions);
    }

    // ---- scene hooks: shortcuts and the focus refresh of the badges ----

    private void onScene(Scene scene) {
        windowSubscription.unsubscribe();
        removeAccelerators();
        if (scene == null) {
            return;
        }
        acceleratorScene = scene;
        accelerators.forEach((combination, action) -> scene.getAccelerators().put(combination, action));
        syncLeaveFocus();
        windowSubscription = scene.windowProperty().subscribe(window -> {
            focusSubscription.unsubscribe();
            if (window != null) {
                // A session left running when the app last closed is offered once the window is up.
                Platform.runLater(() -> sessionBar.offerRecovery(window));
                focusSubscription = window.focusedProperty().subscribe(focused -> {
                    if (focused) {
                        viewModel.refreshBadges();
                    }
                });
            }
        });
    }

    private void removeAccelerators() {
        if (acceleratorScene != null) {
            accelerators.forEach((combination, action) -> acceleratorScene.getAccelerators().remove(combination, action));
            acceleratorScene.getAccelerators().remove(LEAVE_FOCUS);
            acceleratorScene = null;
        }
    }

    // ---- navigation ----

    @Override
    public void showPage(ShellPage page) {
        currentPage = page;
        currentArea = null;
        ViewLoader.LoadedView<?> view = pages.computeIfAbsent(page, shellPages::load);
        display(view.root(), view.controller());
        updateRail();
    }

    @Override
    public void showArea(AreaId areaId) {
        if (viewModel.area(areaId).isEmpty()) {
            return;
        }
        currentPage = null;
        currentArea = areaId;
        AreaPage page = areaPage(areaId);
        display(page.root(), null);
        updateRail();
    }

    @Override
    public void showGoal(AreaId areaId, GoalId goalId) {
        showArea(areaId);
        if (areaPages.containsKey(areaId)) {
            areaPages.get(areaId).controller().openGoal(goalId);
        }
    }

    @Override
    public void showStep(AreaId areaId, GoalId goalId, StepId stepId) {
        showArea(areaId);
        if (areaPages.containsKey(areaId)) {
            areaPages.get(areaId).controller().openStep(goalId, stepId);
        }
    }

    @Override
    public Object pageController(ShellPage page) {
        ViewLoader.LoadedView<?> view = pages.get(page);
        return view == null ? null : view.controller();
    }

    /** Where the window is now, to come back to after the shell has been rebuilt. */
    public Location location() {
        return new Location(currentPage, currentArea);
    }

    public void restore(Location where) {
        if (where == null) {
            return;
        }
        if (where.page() != null) {
            showPage(where.page());
        } else if (where.area() != null) {
            showArea(where.area());
        }
    }

    /** Where a later version docks the study-session bar; it takes no space while empty. */
    public StackPane dock() {
        return dock;
    }

    private AreaPage areaPage(AreaId areaId) {
        return areaPages.computeIfAbsent(areaId, id -> {
            ViewLoader.LoadedView<AreaTabController> view = viewLoader.load(AreaTabController.class);
            view.controller().show(id);
            return new AreaPage(view.root(), view.controller());
        });
    }

    private void display(Node page, Object controller) {
        if (page == shown) {
            return;
        }
        shown = page;
        content.getChildren().setAll(page);
        Motion.fadeIn(page);
        if (controller instanceof PageLifecycle lifecycle) {
            lifecycle.shown();
        }
    }

    /** Highlights the rail entry or area row of the page that is showing. */
    private void updateRail() {
        navItems.forEach((page, item) -> item.button.pseudoClassStateChanged(CURRENT, page == currentPage));
        selectCurrentArea();
    }

    /** Makes the list view's selection match the area being shown (none while a fixed page shows). */
    private void selectCurrentArea() {
        boolean before = syncingSelection;
        syncingSelection = true;
        try {
            if (currentArea == null) {
                areaList.getSelectionModel().clearSelection();
                return;
            }
            viewModel.areas().stream()
                    .filter(area -> area.id().equals(currentArea))
                    .findFirst()
                    .ifPresentOrElse(areaList.getSelectionModel()::select, areaList.getSelectionModel()::clearSelection);
        } finally {
            syncingSelection = before;
        }
    }

    /** Pages of deleted areas are disposed; if the shown area is gone the window falls back to Today. */
    private void onAreasChanged() {
        Set<AreaId> present = new HashSet<>();
        viewModel.areas().forEach(area -> present.add(area.id()));
        areaPages.entrySet().removeIf(entry -> {
            boolean gone = !present.contains(entry.getKey());
            if (gone) {
                entry.getValue().controller().dispose();
            }
            return gone;
        });
        if (currentArea != null && !present.contains(currentArea)) {
            showPage(ShellPage.TODAY);
        }
    }

    /** Stops following data changes and disposes the area pages; the shell is about to be replaced. */
    public void dispose() {
        sceneSubscription.unsubscribe();
        windowSubscription.unsubscribe();
        focusSubscription.unsubscribe();
        focusModeSubscription.unsubscribe();
        removeAccelerators();
        sessionBar.dispose();
        viewModel.dispose();
        pages.values().forEach(view -> {
            if (view.controller() instanceof PageLifecycle lifecycle) {
                lifecycle.disposed();
            }
        });
        areaPages.values().forEach(page -> page.controller().dispose());
        areaPages.clear();
    }

    // ---- areas ----

    @FXML
    private void onNewArea() {
        dialogs.show(window(), AreaDialogController.class, form -> {
        }).ifPresent(created -> showArea(created.id()));
    }

    private void editArea(AreaSummary area) {
        dialogs.show(window(), AreaDialogController.class, form -> form.edit(area));
    }

    private void deleteArea(AreaSummary area) {
        if (confirmations.confirmDelete(window(), "area", area.name(), DeleteConsequences.ofArea(area.goalCount()))) {
            viewModel.deleteArea(area.id());
        }
    }

    private Window window() {
        return root.getScene().getWindow();
    }

    /** The rail's area rows: edit, delete and reorder (menu, drag and drop, Alt+Up / Alt+Down). */
    private final class AreaActions implements AreaRailCell.Actions {

        @Override
        public void edit(AreaSummary area) {
            editArea(area);
        }

        @Override
        public void delete(AreaSummary area) {
            deleteArea(area);
        }

        @Override
        public void step(AreaSummary area, int direction) {
            showArea(area.id());
            viewModel.moveArea(area.id(), direction < 0 ? MoveDirection.UP : MoveDirection.DOWN);
        }

        @Override
        public void moveTo(AreaSummary area, int index) {
            showArea(area.id());
            viewModel.moveAreaTo(area.id(), index);
        }
    }

    /** A rail entry: icon, name and a pill with a count (hidden at 0). */
    private final class NavItem {

        private final Button button = new Button();

        NavItem(ShellPage page) {
            Label title = new Label(messages.get(page.titleKey()));
            title.getStyleClass().add("nav-item-title");
            Label badge = new Label();
            badge.getStyleClass().add("nav-badge");
            badge.managedProperty().bind(badge.visibleProperty());
            badge.setVisible(false);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            HBox row = new HBox(10, new FontIcon(page.icon()), title, spacer, badge);
            row.setAlignment(Pos.CENTER_LEFT);
            button.setGraphic(row);
            button.getStyleClass().add("nav-item");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(click -> showPage(page));
            button.setAccessibleText(title.getText());
            String shortcut = shortcutOf(page);
            button.setTooltip(new Tooltip(shortcut == null ? title.getText() : title.getText() + " (" + shortcut + ")"));

            viewModel.badgeProperty(page).subscribe(count -> {
                int total = count.intValue();
                badge.setVisible(total > 0);
                badge.setText(total > 99 ? "99+" : String.valueOf(total));
                button.pseudoClassStateChanged(HAS_BADGE, total > 0);
                button.setAccessibleText(total > 0 ? title.getText() + ", " + badge.getText() : title.getText());
            });
        }
    }

    private static String shortcutOf(ShellPage page) {
        if (page == ShellPage.SETTINGS) {
            return "Ctrl+,";
        }
        int index = List.of(RAIL_PAGES).indexOf(page);
        return index < 0 ? null : "Ctrl+" + (index + 1);
    }
}
