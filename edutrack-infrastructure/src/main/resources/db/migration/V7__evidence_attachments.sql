-- EduTrack schema v7: richer resource types, attached files and evidence of learning.

-- More kinds of resource. The check was created inline in V2, so PostgreSQL named it itself:
-- find it by what it checks rather than by that generated name.
do $$
declare
    old_check text;
begin
    for old_check in
        select conname
        from pg_constraint
        where conrelid = 'learning_resource'::regclass
          and contype = 'c'
          and pg_get_constraintdef(oid) like '%COURSE%'
    loop
        execute format('alter table learning_resource drop constraint %I', old_check);
    end loop;
end $$;

alter table learning_resource add constraint learning_resource_type_check
    check (type in ('COURSE', 'ARTICLE', 'VIDEO', 'BOOK', 'DOCUMENTATION',
                    'PODCAST', 'REPOSITORY', 'TOOL', 'COMMUNITY', 'PERSON', 'OTHER'));

-- Files attached to a step or a goal. Only the metadata is stored here; the bytes live under the
-- app's files folder at stored_path. Rows go with their owner (cascade), the files are cleaned
-- up by the application at the next start.
create table attachment (
    id            uuid         primary key,
    goal_id       uuid         references goal (id) on delete cascade,
    step_id       uuid         references roadmap_step (id) on delete cascade,
    original_name varchar(255) not null,
    stored_path   varchar(400) not null unique,
    mime_type     varchar(120),
    size_bytes    bigint       not null check (size_bytes > 0 and size_bytes <= 209715200),
    added_at      timestamptz  not null,
    version       bigint       not null default 0,
    -- exactly one owner
    check ((goal_id is null) <> (step_id is null))
);

create index attachment_goal_id_idx on attachment (goal_id) where goal_id is not null;
create index attachment_step_id_idx on attachment (step_id) where step_id is not null;

-- Proof of learning for a goal, optionally tied to a step. Losing the step or the file keeps the
-- evidence (the step link / file reference is cleared); competency_id gets its foreign key when
-- competencies exist.
create table evidence (
    id            uuid          primary key,
    goal_id       uuid          not null references goal (id) on delete cascade,
    step_id       uuid          references roadmap_step (id) on delete set null,
    kind          varchar(20)   not null check (kind in ('PROJECT', 'RECORDING', 'ESSAY', 'CERTIFICATE', 'PRESENTATION', 'OTHER')),
    title         varchar(160)  not null,
    taken_on      date          not null,
    url           varchar(2048),
    attachment_id uuid          references attachment (id) on delete set null,
    competency_id uuid,
    note          varchar(2000),
    created_at    timestamptz   not null,
    version       bigint        not null default 0
);

create index evidence_goal_id_idx on evidence (goal_id);
create index evidence_step_id_idx on evidence (step_id);
create index evidence_attachment_id_idx on evidence (attachment_id);
create index evidence_competency_id_idx on evidence (competency_id);
