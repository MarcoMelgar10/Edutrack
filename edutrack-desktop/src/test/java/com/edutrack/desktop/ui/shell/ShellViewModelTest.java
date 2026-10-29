package com.edutrack.desktop.ui.shell;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.desktop.ui.common.DataChanges;
import com.edutrack.desktop.ui.theme.ThemeManager;
import com.edutrack.domain.area.AreaIcon;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.MoveDirection;
import com.edutrack.domain.progress.Progress;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShellViewModelTest {

    private final AreaService areaService = mock(AreaService.class);
    private final DataChanges dataChanges = new DataChanges();
    private final AtomicInteger dueCards = new AtomicInteger();

    private final AreaSummary english = area("English", AreaIcon.GLOBE, "45");
    private final AreaSummary tech = area("Technologies", AreaIcon.CODE, "10");
    private final AreaSummary consulting = area("Consulting", AreaIcon.BRIEFCASE, "0");

    private static AreaSummary area(String name, AreaIcon icon, String progress) {
        return new AreaSummary(AreaId.newId(), name, null, icon, 2, new Progress(new BigDecimal(progress)));
    }

    private NavBadgeSource badge(ShellPage page, AtomicInteger count) {
        return new NavBadgeSource() {
            @Override
            public ShellPage page() {
                return page;
            }

            @Override
            public int count() {
                return count.get();
            }
        };
    }

    private ShellViewModel viewModel(NavBadgeSource... sources) {
        Map<String, Object> beans = new LinkedHashMap<>();
        for (int index = 0; index < sources.length; index++) {
            beans.put("source" + index, sources[index]);
        }
        ObjectProvider<NavBadgeSource> provider = new StaticListableBeanFactory(beans).getBeanProvider(NavBadgeSource.class);
        return new ShellViewModel(new ThemeManager(), areaService, dataChanges, provider);
    }

    private void areasAre(AreaSummary... areas) {
        when(areaService.listAreas()).thenReturn(List.of(areas));
    }

    @Test
    void theRailListsTheAreasInTheirOrder() {
        areasAre(english, tech, consulting);

        ShellViewModel viewModel = viewModel();

        assertThat(viewModel.areas()).containsExactly(english, tech, consulting);
        assertThat(viewModel.area(tech.id())).contains(tech);
        assertThat(viewModel.area(AreaId.newId())).isEmpty();
    }

    @Test
    void theRailFollowsDataChanges() {
        areasAre(english);
        ShellViewModel viewModel = viewModel();
        areasAre(english, tech);

        dataChanges.markChanged();

        assertThat(viewModel.areas()).containsExactly(english, tech);
    }

    @Test
    void aDisposedShellStopsFollowingDataChanges() {
        areasAre(english);
        ShellViewModel viewModel = viewModel();
        viewModel.dispose();
        areasAre(english, tech);

        dataChanges.markChanged();

        assertThat(viewModel.areas()).containsExactly(english);
    }

    @Test
    void badgesComeFromTheSourcesOfEachPage() {
        areasAre();
        dueCards.set(12);
        AtomicInteger routines = new AtomicInteger(3);

        ShellViewModel viewModel = viewModel(badge(ShellPage.REVIEW, dueCards), badge(ShellPage.TODAY, routines));

        assertThat(viewModel.badgeProperty(ShellPage.REVIEW).get()).isEqualTo(12);
        assertThat(viewModel.badgeProperty(ShellPage.TODAY).get()).isEqualTo(3);
        assertThat(viewModel.badgeProperty(ShellPage.JOURNAL).get()).isZero();
    }

    @Test
    void severalSourcesForOnePageAreAdded() {
        areasAre();

        ShellViewModel viewModel = viewModel(badge(ShellPage.TODAY, new AtomicInteger(2)),
                badge(ShellPage.TODAY, new AtomicInteger(5)));

        assertThat(viewModel.badgeProperty(ShellPage.TODAY).get()).isEqualTo(7);
    }

    @Test
    void badgesAreReadAgainOnDataChangesAndOnDemand() {
        areasAre();
        dueCards.set(4);
        ShellViewModel viewModel = viewModel(badge(ShellPage.REVIEW, dueCards));

        dueCards.set(9);
        dataChanges.markChanged();
        assertThat(viewModel.badgeProperty(ShellPage.REVIEW).get()).isEqualTo(9);

        dueCards.set(0);
        viewModel.refreshBadges(); // what the window does when it gains focus
        assertThat(viewModel.badgeProperty(ShellPage.REVIEW).get()).isZero();
    }

    @Test
    void aNegativeCountShowsNoBadge() {
        areasAre();

        ShellViewModel viewModel = viewModel(badge(ShellPage.REVIEW, new AtomicInteger(-3)));

        assertThat(viewModel.badgeProperty(ShellPage.REVIEW).get()).isZero();
    }

    @Test
    void movingAnAreaAsksTheServiceAndReloadsTheRail() {
        areasAre(english, tech);
        ShellViewModel viewModel = viewModel();
        areasAre(tech, english);

        viewModel.moveArea(tech.id(), MoveDirection.UP);

        verify(areaService).moveArea(tech.id(), MoveDirection.UP);
        assertThat(viewModel.areas()).containsExactly(tech, english);
    }

    @Test
    void draggingAnAreaMovesItOnePlaceAtATime() {
        areasAre(english, tech, consulting);
        ShellViewModel viewModel = viewModel();

        viewModel.moveAreaTo(english.id(), 2);
        verify(areaService, times(2)).moveArea(english.id(), MoveDirection.DOWN);

        viewModel.moveAreaTo(consulting.id(), 0);
        verify(areaService, times(2)).moveArea(consulting.id(), MoveDirection.UP);
    }

    @Test
    void droppingAnAreaWhereItIsDoesNothing() {
        areasAre(english, tech);
        ShellViewModel viewModel = viewModel();

        viewModel.moveAreaTo(english.id(), 0);
        viewModel.moveAreaTo(AreaId.newId(), 1);

        verify(areaService, never()).moveArea(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletingAnAreaSignalsTheChange() {
        areasAre(english);
        ShellViewModel viewModel = viewModel();
        AtomicInteger signals = new AtomicInteger();
        dataChanges.subscribe(signals::incrementAndGet);

        viewModel.deleteArea(english.id());

        verify(areaService).deleteArea(english.id());
        assertThat(signals).hasValue(1);
    }
}
