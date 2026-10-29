package com.edutrack.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * One row in every kind of table, written with plain SQL so the search and backup tests do not depend on the
 * adapters. Each searchable text carries its own word ({@code alpha}, {@code bravo}, …) so a query finds one thing.
 */
public final class SeedData {

    public static final UUID AREA = id(1);
    public static final UUID GOAL = id(2);
    public static final UUID STEP = id(3);
    public static final UUID RECORD = id(4);
    public static final UUID GOAL_RECORD = id(5);
    public static final UUID RESOURCE = id(6);
    public static final UUID RESULT = id(7);
    public static final UUID CARD = id(8);
    public static final UUID JOURNAL_NOTE = id(9);
    public static final UUID EVIDENCE = id(10);
    public static final UUID COMPETENCY = id(11);
    public static final UUID MEASURE = id(12);
    public static final UUID ROUTINE = id(13);
    public static final UUID SESSION = id(14);
    public static final UUID ATTACHMENT = id(15);
    public static final UUID JOURNAL_CHECK_IN = id(16);

    public static final String ATTACHMENT_PATH = "00000000-0000-0000-0000-00000000000f/talk.txt";

    private SeedData() {
    }

    public static UUID id(int n) {
        return new UUID(0, n);
    }

    public static void insert(JdbcTemplate jdbc) {
        jdbc.update("insert into area (id, name, description, icon, position, created_at) "
                + "values (?, 'Idiomas', 'Inglés y más', 'GLOBE', 0, now())", AREA);
        jdbc.update("insert into goal (id, area_id, title, description, deadline, position, created_at, weekly_target_minutes) "
                + "values (?, ?, 'Alpha del examen', 'Canción ñandú — acentos', date '2027-03-01', 0, now(), 300)", GOAL, AREA);
        jdbc.update("insert into roadmap_step (id, goal_id, position, title, description, estimated_hours, status, completed_at, planned_for) "
                + "values (?, ?, 0, 'Bravo present perfect', 'Pasos con detalle', 3.5, 'DONE', now(), date '2026-11-02')", STEP, GOAL);
        jdbc.update("insert into learning_record (id, step_id, notes, updated_at) values (?, ?, ?, now())", RECORD, STEP,
                "Charlie notes\n- [ ] tarea con \"comillas\" y \\ barra\n\ttab y emoji 🎧\n");
        jdbc.update("insert into learning_record (id, goal_id, notes, updated_at) values (?, ?, '', now())", GOAL_RECORD, GOAL);
        jdbc.update("insert into learning_resource (id, record_id, position, title, url, type, reviewed) "
                + "values (?, ?, 0, 'Delta podcast', 'https://example.com/delta', 'PODCAST', true)", RESOURCE, RECORD);
        jdbc.update("insert into learning_exercise (id, record_id, position, title, done, done_at) "
                + "values (?, ?, 0, 'Write ten sentences', true, now())", id(100), RECORD);
        jdbc.update("insert into competency (id, goal_id, name, description, level, target_level, position, created_at) "
                + "values (?, ?, 'India listening', 'Understand fast speech', 3, 5, 0, now())", COMPETENCY, GOAL);
        jdbc.update("insert into competency_level_change (competency_id, changed_on, level) values (?, date '2026-10-01', 2)", COMPETENCY);
        jdbc.update("insert into competency_level_change (competency_id, changed_on, level) values (?, date '2026-10-20', 3)", COMPETENCY);
        jdbc.update("insert into step_competency (step_id, competency_id) values (?, ?)", STEP, COMPETENCY);
        jdbc.update("insert into measure (id, goal_id, name, unit, max_value, target, direction, combine_rule, competency_id, position, created_at) "
                + "values (?, ?, 'Overall band', 'band', 9, 7, 'HIGHER_IS_BETTER', 'AVERAGE', ?, 0, now())", MEASURE, GOAL, COMPETENCY);
        jdbc.update("insert into learning_result (id, record_id, position, title, score, max_score, taken_on, note, measure_id, part) "
                + "values (?, ?, 0, 'Echo mock exam', 6.5, 9, date '2026-10-01', 'Listening was hard', ?, 'Listening')", RESULT, RECORD, MEASURE);
        jdbc.update("insert into practice_routine (id, goal_id, step_id, title, cadence, times_per_week, target_minutes, active, position, created_at) "
                + "values (?, ?, ?, 'Shadowing', 'DAILY', 7, 15, true, 0, now())", ROUTINE, GOAL, STEP);
        jdbc.update("insert into study_session (id, goal_id, step_id, routine_id, started_at, minutes, note, source, created_at) "
                + "values (?, ?, ?, ?, timestamptz '2026-10-27 18:05:00+00', 42, 'Focused', 'TIMER', now())", SESSION, GOAL, STEP, ROUTINE);
        jdbc.update("insert into card (id, goal_id, step_id, front, back, tags, reversible, created_at) "
                + "values (?, ?, ?, 'Foxtrot front', 'Back side', '{vocab,\"two words\"}', true, now())", CARD, GOAL, STEP);
        jdbc.update("insert into card_schedule (card_id, side, ease, interval_days, repetitions, lapses, due_on, last_reviewed_on) "
                + "values (?, 'FORWARD', 2.36, 6, 2, 1, date '2026-11-03', date '2026-10-28')", CARD);
        jdbc.update("insert into card_schedule (card_id, side, ease, interval_days, repetitions, lapses, due_on, last_reviewed_on) "
                + "values (?, 'REVERSE', 2.50, 0, 0, 0, date '2026-10-29', null)", CARD);
        jdbc.update("insert into review_log (id, card_id, side, reviewed_at, grade, seconds, was_new) "
                + "values (?, ?, 'FORWARD', timestamptz '2026-10-28 09:30:15.123456+00', 'GOOD', 12, false)", id(101), CARD);
        jdbc.update("insert into journal_entry (id, kind, written_on, title, body, area_id, goal_id, step_id, confidence, week_minutes, created_at, updated_at) "
                + "values (?, 'NOTE', date '2026-10-28', null, 'Golf reflections about the week', ?, ?, null, null, null, now(), now())",
                JOURNAL_NOTE, AREA, GOAL);
        jdbc.update("insert into journal_entry (id, kind, written_on, title, body, confidence, week_minutes, created_at, updated_at) "
                + "values (?, 'CHECK_IN', date '2026-10-25', 'Weekly check-in', '### Went well', 4, 180, now(), now())", JOURNAL_CHECK_IN);
        jdbc.update("insert into attachment (id, goal_id, original_name, stored_path, mime_type, size_bytes, added_at) "
                + "values (?, ?, 'talk.txt', ?, 'text/plain', 11, now())", ATTACHMENT, GOAL, ATTACHMENT_PATH);
        jdbc.update("insert into evidence (id, goal_id, step_id, kind, title, taken_on, url, attachment_id, competency_id, note, created_at) "
                + "values (?, ?, ?, 'RECORDING', 'Hotel speaking recording', date '2026-10-26', null, ?, ?, 'My best take', now())",
                EVIDENCE, GOAL, STEP, ATTACHMENT, COMPETENCY);
    }
}
