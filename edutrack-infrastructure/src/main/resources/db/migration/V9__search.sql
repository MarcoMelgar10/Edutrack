-- EduTrack schema v9: full-text search.
-- Every table with text the learner writes gets a generated tsvector column and a GIN index. The 'simple'
-- configuration is used because notes mix languages: words are only lower-cased, never stemmed. The
-- columns are maintained by PostgreSQL itself; the JPA entities do not map them.

alter table goal
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(description, ''))) stored;
create index goal_search_idx on goal using gin (search);

alter table roadmap_step
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(description, ''))) stored;
create index roadmap_step_search_idx on roadmap_step using gin (search);

alter table learning_record
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(notes, ''))) stored;
create index learning_record_search_idx on learning_record using gin (search);

alter table learning_resource
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(url, ''))) stored;
create index learning_resource_search_idx on learning_resource using gin (search);

alter table learning_result
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(note, ''))) stored;
create index learning_result_search_idx on learning_result using gin (search);

alter table card
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(front, '') || ' ' || coalesce(back, ''))) stored;
create index card_search_idx on card using gin (search);

alter table journal_entry
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(body, ''))) stored;
create index journal_entry_search_idx on journal_entry using gin (search);

alter table evidence
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(title, '') || ' ' || coalesce(note, ''))) stored;
create index evidence_search_idx on evidence using gin (search);

alter table competency
    add column search tsvector generated always as
        (to_tsvector('simple', coalesce(name, '') || ' ' || coalesce(description, ''))) stored;
create index competency_search_idx on competency using gin (search);
