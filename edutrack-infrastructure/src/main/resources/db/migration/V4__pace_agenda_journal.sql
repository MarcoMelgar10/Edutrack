-- EduTrack schema v4: weekly target, planned days and the journal.
-- A goal may have a weekly study target (minutes), a step a planned day. Journal entries link to an
-- area, goal and step by id, but a reflection outlives what it was about: deleting the target
-- only clears the link (ON DELETE SET NULL).

alter table goal
    add column weekly_target_minutes integer check (weekly_target_minutes between 1 and 10080);

alter table roadmap_step
    add column planned_for date;

create table journal_entry (
    id           uuid         primary key,
    kind         varchar(10)  not null check (kind in ('NOTE', 'CHECK_IN')),
    written_on   date         not null,
    title        varchar(120),
    body         text         not null default '',
    area_id      uuid         references area (id) on delete set null,
    goal_id      uuid         references goal (id) on delete set null,
    step_id      uuid         references roadmap_step (id) on delete set null,
    confidence   smallint     check (confidence between 1 and 5),
    week_minutes integer      check (week_minutes >= 0),
    created_at   timestamptz  not null,
    updated_at   timestamptz  not null,
    version      bigint       not null default 0,
    -- only a check-in has a confidence, and it always has one
    check ((kind = 'CHECK_IN') = (confidence is not null))
);

create index journal_entry_written_on_idx on journal_entry (written_on);
create index journal_entry_goal_id_idx on journal_entry (goal_id);
create index journal_entry_area_id_idx on journal_entry (area_id);
create index journal_entry_step_id_idx on journal_entry (step_id);
