-- EduTrack schema v5: competencies and measures.
-- A competency is something a goal builds (Listening, SQL) with a self-assessed level 0-5, a dated level
-- history and the roadmap steps that contribute to it. A measure is a number a goal tracks over time (an
-- exam band, a speed); results of learning records can be filed under a measure, optionally as one part of
-- an attempt. Everything belongs to a goal and goes with it (ON DELETE CASCADE).

create table competency (
    id           uuid         primary key,
    goal_id      uuid         not null references goal (id) on delete cascade,
    name         varchar(80)  not null,
    description  text,
    level        smallint     not null default 0 check (level between 0 and 5),
    target_level smallint     not null check (target_level between 1 and 5),
    position     integer      not null check (position >= 0),
    created_at   timestamptz  not null,
    version      bigint       not null default 0
);

create index competency_goal_id_idx on competency (goal_id);

-- One entry per day: changing the level twice in a day keeps the last value.
create table competency_level_change (
    competency_id uuid     not null references competency (id) on delete cascade,
    changed_on    date     not null,
    level         smallint not null check (level between 0 and 5),
    primary key (competency_id, changed_on)
);

-- Roadmap steps that contribute to a competency.
create table step_competency (
    step_id       uuid not null references roadmap_step (id) on delete cascade,
    competency_id uuid not null references competency (id) on delete cascade,
    primary key (step_id, competency_id)
);

create index step_competency_competency_id_idx on step_competency (competency_id);

create table measure (
    id            uuid          primary key,
    goal_id       uuid          not null references goal (id) on delete cascade,
    name          varchar(80)   not null,
    unit          varchar(20),
    max_value     numeric(8, 2) check (max_value > 0),
    target        numeric(8, 2) check (target >= 0),
    direction     varchar(20)   not null default 'HIGHER_IS_BETTER'
                                check (direction in ('HIGHER_IS_BETTER', 'LOWER_IS_BETTER')),
    combine_rule  varchar(20)   not null default 'NONE'
                                check (combine_rule in ('NONE', 'AVERAGE', 'SUM')),
    -- deleting the competency keeps the measure
    competency_id uuid          references competency (id) on delete set null,
    position      integer       not null check (position >= 0),
    created_at    timestamptz   not null,
    version       bigint        not null default 0,
    check (max_value is null or target is null or target <= max_value)
);

create index measure_goal_id_idx on measure (goal_id);
create index measure_competency_id_idx on measure (competency_id);

-- Results can count towards a measure; deleting the measure keeps the result and just unlinks it.
alter table learning_result
    add column measure_id uuid references measure (id) on delete set null,
    add column part       varchar(60);

create index learning_result_measure_id_idx on learning_result (measure_id);
