-- EduTrack schema v6: flashcards with spaced repetition (SM-2).
-- A card belongs to a goal, optionally to one of its steps (deleting the step keeps the card). Each side
-- studied has its own schedule: FORWARD always, REVERSE only while the card is reversible. Every answer is
-- appended to review_log, from which the daily limits and the study streak are counted.

create table card (
    id         uuid         primary key,
    goal_id    uuid         not null references goal (id) on delete cascade,
    step_id    uuid         references roadmap_step (id) on delete set null,
    front      text         not null,
    back       text         not null,
    tags       text[]       not null default '{}',
    reversible boolean      not null default false,
    created_at timestamptz  not null,
    version    bigint       not null default 0
);

create index card_goal_id_idx on card (goal_id);
create index card_step_id_idx on card (step_id);

create table card_schedule (
    card_id          uuid          not null references card (id) on delete cascade,
    side             varchar(10)   not null check (side in ('FORWARD', 'REVERSE')),
    ease             numeric(4, 2) not null check (ease >= 1.30),
    interval_days    integer       not null check (interval_days >= 0),
    repetitions      integer       not null check (repetitions >= 0),
    lapses           integer       not null check (lapses >= 0),
    due_on           date          not null,
    -- null while the side has never been reviewed (a new card)
    last_reviewed_on date,
    primary key (card_id, side)
);

create index card_schedule_due_on_idx on card_schedule (due_on);

create table review_log (
    id          uuid        primary key,
    card_id     uuid        not null references card (id) on delete cascade,
    side        varchar(10) not null check (side in ('FORWARD', 'REVERSE')),
    reviewed_at timestamptz not null,
    grade       varchar(10) not null check (grade in ('AGAIN', 'HARD', 'GOOD', 'EASY')),
    seconds     integer     not null check (seconds >= 0),
    was_new     boolean     not null
);

create index review_log_card_id_idx on review_log (card_id);
create index review_log_reviewed_at_idx on review_log (reviewed_at);
