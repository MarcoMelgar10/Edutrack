package com.edutrack.application.search;

import java.util.Comparator;
import java.util.List;

/**
 * Use case for the global search box: finds goals, steps, notes, resources, results, cards,
 * journal entries, evidence and competencies, grouped by kind so the UI can show one section per kind.
 */
public class SearchService {

    /** The most hits one search returns. */
    public static final int MAX_HITS = 50;

    private static final Comparator<SearchHit> GROUPED = Comparator.comparing(SearchHit::kind)
            .thenComparing(Comparator.comparingDouble(SearchHit::rank).reversed());

    private final SearchIndex index;

    public SearchService(SearchIndex index) {
        this.index = index;
    }

    /**
     * The best {@value #MAX_HITS} hits, grouped by {@link SearchKind} in its declaration order and best
     * match first inside a group. A blank query, or one without any letter or digit, finds nothing.
     */
    public List<SearchHit> search(String query) {
        if (query == null || query.codePoints().noneMatch(Character::isLetterOrDigit)) {
            return List.of();
        }
        return index.search(query, MAX_HITS).stream().sorted(GROUPED).limit(MAX_HITS).toList();
    }
}
