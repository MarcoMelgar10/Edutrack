package com.edutrack.desktop.ui.search;

import com.edutrack.application.area.AreaService;
import com.edutrack.application.area.AreaSummary;
import com.edutrack.application.NotFoundException;
import com.edutrack.application.goal.GoalDetails;
import com.edutrack.application.goal.GoalService;
import com.edutrack.application.search.SearchHit;
import com.edutrack.application.search.SearchKind;
import com.edutrack.application.search.SearchService;
import com.edutrack.desktop.ui.common.Messages;
import com.edutrack.desktop.ui.shell.Navigator;
import com.edutrack.desktop.ui.shell.ShellPage;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * State of the search page and of the quick-search box: runs the query, words where each hit lives
 * ("English › IELTS › Step 3") and knows where a click on it goes. The snippet arrives with the matched
 * words wrapped in guillemets, which become bold pieces.
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class SearchViewModel {

    /** A piece of a snippet; the matched words are bold. */
    public record Piece(String text, boolean bold) {
    }

    /** One hit, ready to show. */
    public record Row(SearchHit hit, String title, String location, List<Piece> snippet) {
    }

    /** The hits of one kind, with the section heading. */
    public record Group(SearchKind kind, String heading, List<Row> rows) {
    }

    private final SearchService searchService;
    private final AreaService areaService;
    private final GoalService goalService;
    private final Navigator navigator;
    private final Messages messages;

    private final ObservableList<Group> groups = FXCollections.observableArrayList();
    private final ObservableList<Group> groupsReadOnly = FXCollections.unmodifiableObservableList(groups);
    private final ReadOnlyStringWrapper status = new ReadOnlyStringWrapper("");

    public SearchViewModel(SearchService searchService, AreaService areaService, GoalService goalService,
                           Navigator navigator, Messages messages) {
        this.searchService = searchService;
        this.areaService = areaService;
        this.goalService = goalService;
        this.navigator = navigator;
        this.messages = messages;
    }

    /** Runs the query and fills the groups; a blank query clears them. */
    public void search(String query) {
        if (query == null || query.isBlank()) {
            groups.clear();
            status.set("");
            return;
        }
        List<SearchHit> hits = searchService.search(query);
        Map<AreaId, String> areas = new HashMap<>();
        for (AreaSummary area : areaService.listAreas()) {
            areas.put(area.id(), area.name());
        }
        Map<GoalId, GoalDetails> goals = new HashMap<>();
        Map<SearchKind, List<Row>> byKind = new LinkedHashMap<>();
        for (SearchHit hit : hits) {
            byKind.computeIfAbsent(hit.kind(), kind -> new ArrayList<>()).add(row(hit, areas, goals));
        }
        List<Group> next = new ArrayList<>();
        byKind.forEach((kind, rows) -> next.add(new Group(kind,
                messages.get("search.kind." + kind.name(), rows.size()), rows)));
        groups.setAll(next);
        status.set(hits.isEmpty() ? messages.get("search.none") : messages.plural("search.count", hits.size()));
    }

    Row row(SearchHit hit, Map<AreaId, String> areas, Map<GoalId, GoalDetails> goals) {
        return new Row(hit, hit.title() == null || hit.title().isBlank() ? messages.get("search.untitled") : hit.title(),
                location(hit, areas, goals), pieces(hit.snippet()));
    }

    /** "English › IELTS › 3. Past tenses", as much of it as the hit has. */
    String location(SearchHit hit, Map<AreaId, String> areas, Map<GoalId, GoalDetails> goals) {
        List<String> parts = new ArrayList<>();
        if (hit.areaId() != null && areas.containsKey(hit.areaId())) {
            parts.add(areas.get(hit.areaId()));
        }
        if (hit.goalId() != null) {
            GoalDetails goal = goals.computeIfAbsent(hit.goalId(), id -> {
                try {
                    return goalService.getGoal(id);
                } catch (NotFoundException gone) {
                    return null;
                }
            });
            if (goal != null) {
                if (hit.kind() != SearchKind.GOAL) {
                    parts.add(goal.title());
                }
                if (hit.stepId() != null && hit.kind() != SearchKind.STEP) {
                    goal.steps().stream().filter(step -> step.id().equals(hit.stepId())).findFirst()
                            .ifPresent(step -> parts.add(step.number() + ". " + step.title()));
                }
            }
        }
        return String.join(" › ", parts);
    }

    /** {@code "the «present» perfect"} → "the ", bold "present", " perfect". */
    static List<Piece> pieces(String snippet) {
        List<Piece> pieces = new ArrayList<>();
        if (snippet == null) {
            return pieces;
        }
        int index = 0;
        while (index < snippet.length()) {
            int open = snippet.indexOf('«', index);
            int close = open < 0 ? -1 : snippet.indexOf('»', open);
            if (open < 0 || close < 0) {
                pieces.add(new Piece(snippet.substring(index), false));
                break;
            }
            if (open > index) {
                pieces.add(new Piece(snippet.substring(index, open), false));
            }
            pieces.add(new Piece(snippet.substring(open + 1, close), true));
            index = close + 1;
        }
        return pieces;
    }

    /** Goes to where the hit lives: the step, the goal, or the Journal. */
    public void open(Row row) {
        SearchHit hit = row.hit();
        try {
            if (hit.goalId() != null && hit.stepId() != null) {
                navigator.openStep(hit.goalId(), hit.stepId());
            } else if (hit.goalId() != null) {
                navigator.openGoal(hit.goalId());
            } else if (hit.kind() == SearchKind.JOURNAL) {
                navigator.open(ShellPage.JOURNAL);
            }
        } catch (NotFoundException gone) {
            // the item was deleted since the search: nothing to open
        }
    }

    /** The first hits across all groups, e.g. for the quick-search list. */
    public List<Row> firstRows(int limit) {
        return groups.stream().flatMap(group -> group.rows().stream()).limit(limit).toList();
    }

    public ObservableList<Group> groups() {
        return groupsReadOnly;
    }

    /** "12 results" or "Nothing found". */
    public ReadOnlyStringProperty statusProperty() {
        return status.getReadOnlyProperty();
    }
}
