package com.edutrack.application.search;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceTest {

    private final List<String> queriesAsked = new ArrayList<>();
    private final List<Integer> limitsAsked = new ArrayList<>();
    private List<SearchHit> indexed = List.of();
    private final SearchService service = new SearchService((query, limit) -> {
        queriesAsked.add(query);
        limitsAsked.add(limit);
        return indexed;
    });

    private static SearchHit hit(SearchKind kind, String title, double rank) {
        return new SearchHit(kind, UUID.randomUUID(), null, null, null, title, title, rank);
    }

    @Test
    void blankOrSymbolOnlyQueriesFindNothingAndDoNotReachTheIndex() {
        indexed = List.of(hit(SearchKind.GOAL, "Anything", 1));

        assertThat(service.search(null)).isEmpty();
        assertThat(service.search("")).isEmpty();
        assertThat(service.search("   ")).isEmpty();
        assertThat(service.search("?! -- ()")).isEmpty();
        assertThat(queriesAsked).isEmpty();
    }

    @Test
    void hitsAreGroupedByKindInDeclarationOrderAndBestRankFirstInsideAGroup() {
        indexed = List.of(
                hit(SearchKind.CARD, "card low", 0.1),
                hit(SearchKind.COMPETENCY, "competency", 0.9),
                hit(SearchKind.GOAL, "goal low", 0.2),
                hit(SearchKind.CARD, "card high", 0.8),
                hit(SearchKind.NOTE, "note", 0.5),
                hit(SearchKind.GOAL, "goal high", 0.7));

        List<SearchHit> hits = service.search("anything");

        assertThat(hits).extracting(SearchHit::title)
                .containsExactly("goal high", "goal low", "note", "card high", "card low", "competency");
    }

    @Test
    void asksTheIndexForTheTextTypedAndAtMostFiftyHits() {
        indexed = List.of(hit(SearchKind.GOAL, "Goal", 1));

        service.search("  present perfect ");

        assertThat(queriesAsked).containsExactly("  present perfect ");
        assertThat(limitsAsked).containsExactly(50);
    }

    @Test
    void neverReturnsMoreThanFiftyHits() {
        List<SearchHit> many = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            many.add(hit(SearchKind.values()[i % SearchKind.values().length], "Hit " + i, i));
        }
        indexed = many;

        assertThat(service.search("hit")).hasSize(50);
    }
}
