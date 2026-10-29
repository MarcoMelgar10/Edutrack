package com.edutrack.infrastructure.search;

import com.edutrack.application.search.SearchHit;
import com.edutrack.application.search.SearchIndex;
import com.edutrack.application.search.SearchKind;
import com.edutrack.domain.area.AreaId;
import com.edutrack.domain.goal.GoalId;
import com.edutrack.domain.goal.StepId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Full-text search over the {@code search} columns added in V9. The user's text is never put into
 * SQL: it is reduced to lower-case words (letters and digits only), joined into a prefix query such as
 * {@code pres:* & perf:*} and bound as a parameter.
 *
 * <p>One statement ranks the matches of every table, keeps the best ones and only then builds the
 * snippets, which is the expensive part for long notes.
 */
@Repository
public class JdbcSearchIndex implements SearchIndex {

    static final int MAX_WORDS = 8;
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");

    private static final String SEARCH = """
            with q as (select to_tsquery('simple', ?) as query)
            select top.kind, top.id, top.area_id, top.goal_id, top.step_id, top.title, top.rank,
                   ts_headline('simple', top.body, q.query, 'MaxWords=18, MinWords=8, StartSel=«, StopSel=»') as snippet
            from (
                select * from (
                    select 'GOAL' as kind, g.id, g.area_id, g.id as goal_id, null::uuid as step_id, g.title as title,
                           coalesce(g.title, '') || ' ' || coalesce(g.description, '') as body,
                           ts_rank(g.search, q.query)::float8 as rank
                    from goal g, q
                    where g.search @@ q.query

                    union all
                    select 'STEP', s.id, g.area_id, g.id, s.id, s.title,
                           coalesce(s.title, '') || ' ' || coalesce(s.description, ''),
                           ts_rank(s.search, q.query)::float8
                    from roadmap_step s join goal g on g.id = s.goal_id, q
                    where s.search @@ q.query

                    union all
                    select 'NOTE', r.id, g.area_id, g.id, r.step_id, coalesce(s.title, g.title),
                           r.notes,
                           ts_rank(r.search, q.query)::float8
                    from learning_record r
                         left join roadmap_step s on s.id = r.step_id
                         join goal g on g.id = coalesce(r.goal_id, s.goal_id), q
                    where r.search @@ q.query

                    union all
                    select 'RESOURCE', res.id, g.area_id, g.id, r.step_id, res.title,
                           coalesce(res.title, '') || ' ' || coalesce(res.url, ''),
                           ts_rank(res.search, q.query)::float8
                    from learning_resource res
                         join learning_record r on r.id = res.record_id
                         left join roadmap_step s on s.id = r.step_id
                         join goal g on g.id = coalesce(r.goal_id, s.goal_id), q
                    where res.search @@ q.query

                    union all
                    select 'RESULT', res.id, g.area_id, g.id, r.step_id, res.title,
                           coalesce(res.title, '') || ' ' || coalesce(res.note, ''),
                           ts_rank(res.search, q.query)::float8
                    from learning_result res
                         join learning_record r on r.id = res.record_id
                         left join roadmap_step s on s.id = r.step_id
                         join goal g on g.id = coalesce(r.goal_id, s.goal_id), q
                    where res.search @@ q.query

                    union all
                    select 'CARD', c.id, g.area_id, g.id, c.step_id, left(c.front, 120),
                           coalesce(c.front, '') || ' ' || coalesce(c.back, ''),
                           ts_rank(c.search, q.query)::float8
                    from card c join goal g on g.id = c.goal_id, q
                    where c.search @@ q.query

                    union all
                    select 'JOURNAL', j.id, coalesce(j.area_id, g.area_id), j.goal_id, j.step_id,
                           coalesce(j.title, left(regexp_replace(j.body, '\\s+', ' ', 'g'), 80)),
                           coalesce(j.title, '') || ' ' || coalesce(j.body, ''),
                           ts_rank(j.search, q.query)::float8
                    from journal_entry j left join goal g on g.id = j.goal_id, q
                    where j.search @@ q.query

                    union all
                    select 'EVIDENCE', e.id, g.area_id, g.id, e.step_id, e.title,
                           coalesce(e.title, '') || ' ' || coalesce(e.note, ''),
                           ts_rank(e.search, q.query)::float8
                    from evidence e join goal g on g.id = e.goal_id, q
                    where e.search @@ q.query

                    union all
                    select 'COMPETENCY', c.id, g.area_id, g.id, null::uuid, c.name,
                           coalesce(c.name, '') || ' ' || coalesce(c.description, ''),
                           ts_rank(c.search, q.query)::float8
                    from competency c join goal g on g.id = c.goal_id, q
                    where c.search @@ q.query
                ) hits
                order by rank desc, kind, id
                limit ?
            ) top, q
            order by top.rank desc, top.kind, top.id
            """;

    private final JdbcTemplate jdbc;

    public JdbcSearchIndex(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SearchHit> search(String query, int limit) {
        String prefixQuery = prefixQuery(query);
        if (prefixQuery.isEmpty() || limit < 1) {
            return List.of();
        }
        return jdbc.query(SEARCH, JdbcSearchIndex::hit, prefixQuery, limit);
    }

    /**
     * {@code "Present  Perf!"} → {@code "present:* & perf:*"}: at most {@value #MAX_WORDS} words, each made only of
     * letters and digits, so the result contains no tsquery syntax. Empty when the text has no word.
     */
    static String prefixQuery(String text) {
        List<String> words = new ArrayList<>();
        Matcher matcher = WORD.matcher(text == null ? "" : text);
        while (matcher.find() && words.size() < MAX_WORDS) {
            words.add(matcher.group().toLowerCase(Locale.ROOT) + ":*");
        }
        return String.join(" & ", words);
    }

    private static SearchHit hit(ResultSet row, int index) throws SQLException {
        UUID areaId = row.getObject("area_id", UUID.class);
        UUID goalId = row.getObject("goal_id", UUID.class);
        UUID stepId = row.getObject("step_id", UUID.class);
        return new SearchHit(SearchKind.valueOf(row.getString("kind")), row.getObject("id", UUID.class),
                areaId == null ? null : new AreaId(areaId), goalId == null ? null : new GoalId(goalId),
                stepId == null ? null : new StepId(stepId), row.getString("title"), row.getString("snippet"),
                row.getDouble("rank"));
    }
}
