-- EduTrack schema v1: areas → goals → roadmap steps.
-- Goals reference their area by id only (separate aggregates); steps belong to their goal.
-- Deleting an area removes its goals, and deleting a goal removes its steps (ON DELETE CASCADE).

create table area (
    id          uuid         primary key,
    name        varchar(80)  not null,
    description text,
    icon        varchar(40)  not null,
    position    integer      not null check (position >= 0),
    created_at  timestamptz  not null,
    version     bigint       not null default 0
);

-- Area names are unique regardless of letter case ("English" = "english").
create unique index area_name_unique on area (lower(name));

create table goal (
    id          uuid         primary key,
    area_id     uuid         not null references area (id) on delete cascade,
    title       varchar(120) not null,
    description text,
    deadline    date,
    position    integer      not null check (position >= 0),
    created_at  timestamptz  not null,
    version     bigint       not null default 0
);

create index goal_area_id_idx on goal (area_id);

create table roadmap_step (
    id              uuid          primary key,
    goal_id         uuid          not null references goal (id) on delete cascade,
    position        integer       not null check (position >= 0),
    title           varchar(120)  not null,
    description     text,
    resource_url    varchar(2048),
    estimated_hours numeric(6, 1) check (estimated_hours >= 0),
    status          varchar(20)   not null check (status in ('NOT_STARTED', 'IN_PROGRESS', 'DONE')),
    completed_at    timestamptz,
    -- completion time exists exactly when the step is done
    check ((status = 'DONE') = (completed_at is not null))
);

create index roadmap_step_goal_id_idx on roadmap_step (goal_id);
