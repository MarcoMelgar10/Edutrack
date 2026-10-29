-- EduTrack schema v2: learning records.
-- A record belongs to exactly one roadmap step (notes, resources, exercises, results) or one goal
-- (its final results) and is deleted with its owner. The single link a step used to carry
-- becomes the first resource of that step's record.

create table learning_record (
    id         uuid        primary key,
    goal_id    uuid        references goal (id) on delete cascade,
    step_id    uuid        references roadmap_step (id) on delete cascade,
    notes      text        not null default '',
    updated_at timestamptz not null,
    version    bigint      not null default 0,
    -- exactly one owner
    check ((goal_id is null) <> (step_id is null))
);

-- at most one record per owner
create unique index learning_record_goal_unique on learning_record (goal_id) where goal_id is not null;
create unique index learning_record_step_unique on learning_record (step_id) where step_id is not null;

create table learning_resource (
    id        uuid          primary key,
    record_id uuid          not null references learning_record (id) on delete cascade,
    position  integer       not null check (position >= 0),
    title     varchar(160)  not null,
    url       varchar(2048),
    type      varchar(20)   not null check (type in ('COURSE', 'ARTICLE', 'VIDEO', 'BOOK', 'DOCUMENTATION', 'OTHER')),
    reviewed  boolean       not null default false
);

create index learning_resource_record_id_idx on learning_resource (record_id);

create table learning_exercise (
    id        uuid         primary key,
    record_id uuid         not null references learning_record (id) on delete cascade,
    position  integer      not null check (position >= 0),
    title     varchar(200) not null,
    done      boolean      not null default false,
    done_at   timestamptz,
    check (done = (done_at is not null))
);

create index learning_exercise_record_id_idx on learning_exercise (record_id);

create table learning_result (
    id        uuid          primary key,
    record_id uuid          not null references learning_record (id) on delete cascade,
    position  integer       not null check (position >= 0),
    title     varchar(160)  not null,
    score     numeric(8, 2) not null check (score >= 0),
    max_score numeric(8, 2) check (max_score > 0),
    taken_on  date          not null,
    note      varchar(500),
    check (max_score is null or score <= max_score)
);

create index learning_result_record_id_idx on learning_result (record_id);

-- Move each step's link into a resource of a new record for that step.
insert into learning_record (id, step_id, notes, updated_at)
select gen_random_uuid(), s.id, '', now()
from roadmap_step s
where s.resource_url is not null;

insert into learning_resource (id, record_id, position, title, url, type, reviewed)
select gen_random_uuid(),
       r.id,
       0,
       left(regexp_replace(s.resource_url, '^https?://(www\.)?', ''), 160),
       s.resource_url,
       'OTHER',
       false
from roadmap_step s
join learning_record r on r.step_id = s.id
where s.resource_url is not null;

alter table roadmap_step drop column resource_url;
