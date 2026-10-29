package com.edutrack.desktop.ui.search;

import com.edutrack.application.NotFoundException;
import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.goal.StepView;
import com.edutrack.application.search.SearchHit;
import com.edutrack.application.search.SearchKind;
import com.edutrack.application.search.SearchService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.desktop.ui.shell.ShellPage;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SearchViewModelTest {

    private final SearchService searchService = mock(SearchService.class);
    private final AreaService areaService = mock(AreaService.class);
    private final GoalService goalService = mock(GoalService.class);
    private final Navigator navigator = mock(Navigator.class);
    private final SearchViewModel viewModel =
            new SearchViewModel(searchService, areaService, goalService, navigator, new Messages());

    private final AreaId area = AreaId.newId();
    private final GoalId goal = GoalId.newId();
    private final StepId step = StepId.newId();

    private SearchHit hit(SearchKind kind, AreaId areaId, GoalId goalId, StepId stepId, String title, String snippet) {
        return new SearchHit(kind, UUID.randomUUID(), areaId, goalId, stepId, title, snippet, 1.0);
    }

    private void world() {
        AreaSummary english = mock(AreaSummary.class);
        when(english.id()).thenReturn(area);
        when(english.name()).thenReturn("English");
        when(areaService.listAreas()).thenReturn(List.of(english));
        StepView stepView = mock(StepView.class);
        when(stepView.id()).thenReturn(step);
        when(stepView.number()).thenReturn(3);
        when(stepView.title()).thenReturn("Past tenses");
        GoalDetails ielts = mock(GoalDetails.class);
        when(ielts.title()).thenReturn("IELTS");
        when(ielts.steps()).thenReturn(List.of(stepView));
        when(goalService.getGoal(goal)).thenReturn(ielts);
    }

    @Test
    void aBlankQueryClearsTheResultsWithoutSearching() {
        world();
        when(searchService.search("tense")).thenReturn(List.of(hit(SearchKind.GOAL, area, goal, null, "IELTS", null)));
        viewModel.search("tense");
        assertThat(viewModel.groups()).hasSize(1);

        viewModel.search("   ");

        assertThat(viewModel.groups()).isEmpty();
        assertThat(viewModel.statusProperty().get()).isEmpty();
        viewModel.search(null);
        assertThat(viewModel.groups()).isEmpty();
    }

    @Test
    void hitsAreGroupedByKindKeepingTheirOrderAndCounted() {
        world();
        when(searchService.search("ielts")).thenReturn(List.of(
                hit(SearchKind.GOAL, area, goal, null, "IELTS", null),
                hit(SearchKind.NOTE, area, goal, step, "Note A", null),
                hit(SearchKind.NOTE, area, goal, step, "Note B", null)));

        viewModel.search("ielts");

        assertThat(viewModel.groups()).extracting(SearchViewModel.Group::kind)
                .containsExactly(SearchKind.GOAL, SearchKind.NOTE);
        assertThat(viewModel.groups()).extracting(SearchViewModel.Group::heading)
                .containsExactly("Goals (1)", "Notes (2)");
        assertThat(viewModel.statusProperty().get()).isEqualTo("3 results");
        assertThat(viewModel.firstRows(2)).extracting(SearchViewModel.Row::title).containsExactly("IELTS", "Note A");
    }

    @Test
    void noHitsSaysNothingWasFound() {
        when(areaService.listAreas()).thenReturn(List.of());
        when(searchService.search("zzz")).thenReturn(List.of());

        viewModel.search("zzz");

        assertThat(viewModel.groups()).isEmpty();
        assertThat(viewModel.statusProperty().get()).startsWith("Nothing found");
    }

    @Test
    void aSingleHitUsesTheSingularStatus() {
        world();
        when(searchService.search("x")).thenReturn(List.of(hit(SearchKind.GOAL, area, goal, null, "IELTS", null)));
        viewModel.search("x");
        assertThat(viewModel.statusProperty().get()).isEqualTo("1 result");
    }

    @Test
    void aHitWithoutATitleIsShownAsUntitled() {
        world();
        when(searchService.search("x")).thenReturn(List.of(hit(SearchKind.NOTE, area, goal, step, " ", null)));
        viewModel.search("x");
        assertThat(viewModel.firstRows(1).get(0).title()).isEqualTo("(untitled)");
    }

    @Test
    void theLocationWritesAreaGoalAndStepAsFarAsTheHitGoes() {
        world();
        Map<AreaId, String> areas = Map.of(area, "English");
        Map<GoalId, GoalDetails> goals = new HashMap<>();

        assertThat(viewModel.location(hit(SearchKind.NOTE, area, goal, step, "n", null), areas, goals))
                .isEqualTo("English › IELTS › 3. Past tenses");
        assertThat(viewModel.location(hit(SearchKind.STEP, area, goal, step, "s", null), areas, goals))
                .isEqualTo("English › IELTS");
        assertThat(viewModel.location(hit(SearchKind.GOAL, area, goal, null, "g", null), areas, goals))
                .isEqualTo("English");
        assertThat(viewModel.location(hit(SearchKind.JOURNAL, null, null, null, "j", null), areas, goals)).isEmpty();
    }

    @Test
    void aGoalDeletedSinceIndexingLeavesTheLocationShorter() {
        GoalId gone = GoalId.newId();
        when(goalService.getGoal(gone)).thenThrow(new NotFoundException("Goal", gone));

        String location = viewModel.location(hit(SearchKind.NOTE, area, gone, null, "n", null),
                Map.of(area, "English"), new HashMap<>());

        assertThat(location).isEqualTo("English");
    }

    @Test
    void snippetsBecomeBoldPiecesAtTheGuillemets() {
        assertThat(SearchViewModel.pieces("the «present» perfect")).containsExactly(
                new SearchViewModel.Piece("the ", false), new SearchViewModel.Piece("present", true),
                new SearchViewModel.Piece(" perfect", false));
        assertThat(SearchViewModel.pieces("«a»«b»")).containsExactly(
                new SearchViewModel.Piece("a", true), new SearchViewModel.Piece("b", true));
        assertThat(SearchViewModel.pieces("broken «open")).containsExactly(
                new SearchViewModel.Piece("broken «open", false));
        assertThat(SearchViewModel.pieces("plain")).containsExactly(new SearchViewModel.Piece("plain", false));
        assertThat(SearchViewModel.pieces(null)).isEmpty();
        assertThat(SearchViewModel.pieces("")).isEmpty();
    }

    private SearchViewModel.Row rowOf(SearchHit hit) {
        return new SearchViewModel.Row(hit, "t", "", List.of());
    }

    @Test
    void clickingAStepHitOpensTheStepAGoalHitTheGoalAndAJournalHitTheJournal() {
        viewModel.open(rowOf(hit(SearchKind.NOTE, area, goal, step, "n", null)));
        verify(navigator).openStep(goal, step);

        viewModel.open(rowOf(hit(SearchKind.GOAL, area, goal, null, "g", null)));
        verify(navigator).openGoal(goal);

        viewModel.open(rowOf(hit(SearchKind.JOURNAL, null, null, null, "j", null)));
        verify(navigator).open(ShellPage.JOURNAL);
    }

    @Test
    void openingSomethingDeletedSinceTheSearchDoesNothing() {
        doThrow(new NotFoundException("Goal", goal)).when(navigator).openGoal(goal);

        viewModel.open(rowOf(hit(SearchKind.GOAL, area, goal, null, "g", null)));

        verify(navigator, never()).open(any());
    }

    @Test
    void everyKindHasItsOwnIcon() {
        assertThat(EnumSet.allOf(SearchKind.class).stream().map(SearchRows::icon).distinct().count())
                .isEqualTo(SearchKind.values().length);
    }

    @Test
    void nothingIsSearchedUntilTheViewModelIsAsked() {
        verifyNoInteractions(searchService);
    }
}
