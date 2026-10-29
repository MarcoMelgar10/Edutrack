package com.edutrack.infrastructure.search;

import com.edutrack.application.search.SearchHit;
import com.edutrack.application.search.SearchKind;
import com.edutrack.infrastructure.SeedData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The real full-text queries against PostgreSQL, over one row of every searchable kind. Rolled back after each test. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcSearchIndex.class)
class JdbcSearchIndexIT {

    @Autowired
    private JdbcSearchIndex index;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        SeedData.insert(jdbc);
    }

    private SearchHit only(String query, SearchKind kind) {
        List<SearchHit> hits = index.search(query, 20);
        assertThat(hits).as("hits for %s", query).hasSize(1);
        assertThat(hits.getFirst().kind()).isEqualTo(kind);
        return hits.getFirst();
    }

    @Test
    void findsEveryKindAndResolvesWhereItLives() {
        SearchHit goal = only("alpha", SearchKind.GOAL);
        assertThat(goal.id()).isEqualTo(SeedData.GOAL);
        assertThat(goal.areaId().value()).isEqualTo(SeedData.AREA);
        assertThat(goal.goalId().value()).isEqualTo(SeedData.GOAL);
        assertThat(goal.stepId()).isNull();

        SearchHit step = only("bravo", SearchKind.STEP);
        assertThat(step.id()).isEqualTo(SeedData.STEP);
        assertThat(step.stepId().value()).isEqualTo(SeedData.STEP);
        assertThat(step.goalId().value()).isEqualTo(SeedData.GOAL);

        SearchHit note = only("charlie", SearchKind.NOTE);
        assertThat(note.id()).isEqualTo(SeedData.RECORD);
        assertThat(note.stepId().value()).isEqualTo(SeedData.STEP);
        assertThat(note.title()).isEqualTo("Bravo present perfect");

        assertThat(only("delta", SearchKind.RESOURCE).stepId().value()).isEqualTo(SeedData.STEP);
        assertThat(only("echo", SearchKind.RESULT).goalId().value()).isEqualTo(SeedData.GOAL);
        assertThat(only("foxtrot", SearchKind.CARD).id()).isEqualTo(SeedData.CARD);

        SearchHit journal = only("golf", SearchKind.JOURNAL);
        assertThat(journal.areaId().value()).isEqualTo(SeedData.AREA);
        assertThat(journal.goalId().value()).isEqualTo(SeedData.GOAL);
        assertThat(journal.title()).startsWith("Golf reflections");

        SearchHit evidence = only("hotel", SearchKind.EVIDENCE);
        assertThat(evidence.stepId().value()).isEqualTo(SeedData.STEP);

        SearchHit competency = only("india", SearchKind.COMPETENCY);
        assertThat(competency.goalId().value()).isEqualTo(SeedData.GOAL);
        assertThat(competency.stepId()).isNull();
    }

    @Test
    void aJournalEntryWithoutLinksStillMatches() {
        SearchHit checkIn = only("went", SearchKind.JOURNAL);

        assertThat(checkIn.id()).isEqualTo(SeedData.JOURNAL_CHECK_IN);
        assertThat(checkIn.areaId()).isNull();
        assertThat(checkIn.goalId()).isNull();
    }

    @Test
    void matchesPrefixesAndEveryWord() {
        assertThat(index.search("pres", 20)).extracting(SearchHit::kind).containsExactly(SearchKind.STEP);
        assertThat(index.search("bravo pres perf", 20)).hasSize(1);
        assertThat(index.search("bravo missingword", 20)).isEmpty();
    }

    @Test
    void ignoresCaseAndKeepsAccents() {
        List<SearchHit> hits = index.search("CANCIÓN", 20);

        assertThat(hits).hasSize(1);
        assertThat(hits.getFirst().kind()).isEqualTo(SearchKind.GOAL);
        assertThat(index.search("ñandú", 20)).hasSize(1);
    }

    @Test
    void snippetsMarkTheMatchWithGuillemets() {
        SearchHit note = only("charlie", SearchKind.NOTE);

        assertThat(note.snippet()).contains("«Charlie»").contains("notes");
    }

    @Test
    void queryTextIsNeverSqlOrQuerySyntax() {
        List<String> hostile = List.of("x'); drop table goal; --", "alpha & | ! ( ) :* <->", "\\", "'", "alpha:*", ":::");

        for (String text : hostile) {
            index.search(text, 20); // must not throw
        }

        assertThat(index.search("alpha & | ! ( )", 20)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from goal", Integer.class)).isEqualTo(1);
    }

    @Test
    void blankSymbolOnlyOrNonPositiveLimitFindNothing() {
        assertThat(index.search("", 20)).isEmpty();
        assertThat(index.search("   ", 20)).isEmpty();
        assertThat(index.search("!!! ???", 20)).isEmpty();
        assertThat(index.search(null, 20)).isEmpty();
        assertThat(index.search("alpha", 0)).isEmpty();
    }

    @Test
    void limitsTheNumberOfHits() {
        assertThat(index.search("e", 3)).hasSizeLessThanOrEqualTo(3);
    }

    @Test
    void prefixQueryKeepsOnlyWordsAndAtMostEightOfThem() {
        assertThat(JdbcSearchIndex.prefixQuery("Present  Perf!")).isEqualTo("present:* & perf:*");
        assertThat(JdbcSearchIndex.prefixQuery("a b c d e f g h i j"))
                .isEqualTo("a:* & b:* & c:* & d:* & e:* & f:* & g:* & h:*");
        assertThat(JdbcSearchIndex.prefixQuery("'; --")).isEmpty();
    }
}
