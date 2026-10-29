-- EduTrack schema v3: study sessions and practice routines.
-- Both belong to a goal and disappear with it (ON DELETE CASCADE). A step is only a label for the
-- time or routine: deleting the step keeps them (ON DELETE SET NULL). A session may point to the
-- routine it was logged for; deleting the routine keeps the session.

create table practice_routine (
    id             uuid         primary key,
    goal_id        uuid         not null references goal (id) on delete cascade,
    step_id        uuid         references roadmap_step (id) on delete set null,
    title          varchar(120) not null,
    cadence        varchar(10)  not null check (cadence in ('DAILY', 'WEEKLY')),
    -- a daily routine is always 7 times a week
    times_per_week integer      not null check (times_per_week between 1 and 7),
    target_minutes integer      not null check (target_minutes between 1 and 600),
    active         boolean      not null default true,
    position       integer      not null check (position >= 0),
    created_at     timestamptz  not null,
    version        bigint       not null default 0,
    check (cadence <> 'DAILY' or times_per_week = 7)
);

create index practice_routine_goal_id_idx on practice_routine (goal_id);
create index practice_routine_step_id_idx on practice_routine (step_id);

create table study_session (
    id         uuid         primary key,
    goal_id    uuid         not null references goal (id) on delete cascade,
    step_id    uuid         references roadmap_step (id) on delete set null,
    routine_id uuid         references practice_routine (id) on delete set null,
    started_at timestamptz  not null,
    minutes    integer      not null check (minutes between 1 and 1440),
    note       varchar(500),
    source     varchar(10)  not null check (source in ('TIMER', 'MANUAL')),
    created_at timestamptz  not null,
    version    bigint       not null default 0
);

create index study_session_goal_started_idx on study_session (goal_id, started_at);
create index study_session_started_idx on study_session (started_at);
create index study_session_step_id_idx on study_session (step_id);
create index study_session_routine_id_idx on study_session (routine_id);
