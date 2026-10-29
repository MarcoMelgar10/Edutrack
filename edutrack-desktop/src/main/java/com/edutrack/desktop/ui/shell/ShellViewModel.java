package com.edutrack.desktop.ui.shell;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.theme.Theme;
import com.edutrack.desktop.ui.theme.ThemeManager;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.MoveDirection;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.util.Subscription;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * State behind the main window's left rail: the areas in their order, the numbers on the rail
 * entries and the theme switch. It follows {@link DataChanges} until {@link #dispose()}, which the
 * shell calls when it is replaced (e.g. after a language switch).
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ShellViewModel {

    private final ThemeManager themeManager;
    private final AreaService areaService;
    private final DataChanges dataChanges;
    private final ObjectProvider<NavBadgeSource> badgeSources;

    private final ReadOnlyBooleanWrapper darkMode = new ReadOnlyBooleanWrapper();
    private final ObservableList<AreaSummary> areas = FXCollections.observableArrayList();
    private final ObservableList<AreaSummary> areasReadOnly = FXCollections.unmodifiableObservableList(areas);
    private final Map<ShellPage, IntegerProperty> badges = new EnumMap<>(ShellPage.class);
    private final Subscription dataSubscription;

    public ShellViewModel(ThemeManager themeManager, AreaService areaService, DataChanges dataChanges,
                          ObjectProvider<NavBadgeSource> badgeSources) {
        this.themeManager = themeManager;
        this.areaService = areaService;
        this.dataChanges = dataChanges;
        this.badgeSources = badgeSources;
        for (ShellPage page : ShellPage.values()) {
            badges.put(page, new SimpleIntegerProperty());
        }
        darkMode.bind(themeManager.themeProperty().isEqualTo(Theme.DARK));
        refresh();
        dataSubscription = dataChanges.subscribe(this::refresh);
    }

    public void dispose() {
        dataSubscription.unsubscribe();
    }

    /** Reloads the areas and the rail numbers. */
    public void refresh() {
        areas.setAll(areaService.listAreas());
        refreshBadges();
    }

    /** Asks every {@link NavBadgeSource} for its count; pages without a source show none. */
    public void refreshBadges() {
        Map<ShellPage, Integer> totals = new EnumMap<>(ShellPage.class);
        badgeSources.orderedStream().forEach(source -> totals.merge(source.page(), Math.max(0, source.count()), Integer::sum));
        badges.forEach((page, badge) -> badge.set(totals.getOrDefault(page, 0)));
    }

    /** The number on a rail entry; 0 means no badge. */
    public ReadOnlyIntegerProperty badgeProperty(ShellPage page) {
        return badges.get(page);
    }

    /** Areas in rail order. */
    public ObservableList<AreaSummary> areas() {
        return areasReadOnly; // one shared wrapper: it only weakly listens to the source list
    }

    public Optional<AreaSummary> area(AreaId id) {
        return areas.stream().filter(area -> area.id().equals(id)).findFirst();
    }

    /** Swaps the area with its neighbour; moving past either end does nothing. */
    public void moveArea(AreaId id, MoveDirection direction) {
        areaService.moveArea(id, direction);
        dataChanges.markChanged();
    }

    /** Moves the area to position {@code index} (drag and drop), one swap at a time. */
    public void moveAreaTo(AreaId id, int index) {
        int from = indexOf(id);
        if (from < 0 || index == from) {
            return;
        }
        MoveDirection direction = index < from ? MoveDirection.UP : MoveDirection.DOWN;
        for (int swaps = Math.abs(index - from); swaps > 0; swaps--) {
            areaService.moveArea(id, direction);
        }
        dataChanges.markChanged();
    }

    public void deleteArea(AreaId id) {
        areaService.deleteArea(id);
        dataChanges.markChanged();
    }

    private int indexOf(AreaId id) {
        for (int index = 0; index < areas.size(); index++) {
            if (areas.get(index).id().equals(id)) {
                return index;
            }
        }
        return -1;
    }

    public ReadOnlyBooleanProperty darkModeProperty() {
        return darkMode.getReadOnlyProperty();
    }

    public void toggleTheme() {
        themeManager.toggle();
    }
}
