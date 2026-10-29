package com.edutrack.application.search;

import java.util.List;

/** Port for the full-text index over everything the learner wrote. Implemented by the infrastructure layer. */
public interface SearchIndex {

    /**
     * The best {@code limit} hits for the words of {@code query}, best first. Every word must match
     * (as a prefix) and the query may be any text the user typed.
     */
    List<SearchHit> search(String query, int limit);
}
