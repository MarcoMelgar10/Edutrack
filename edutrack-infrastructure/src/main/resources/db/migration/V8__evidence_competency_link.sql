-- EduTrack schema v8: evidence can prove a competency. V7 (evidence) and V5 (competency) were written
-- side by side, so the link gets its foreign key here. A deleted competency keeps its evidence.

update evidence e set competency_id = null
where competency_id is not null and not exists (select 1 from competency c where c.id = e.competency_id);

alter table evidence
    add constraint evidence_competency_fk foreign key (competency_id) references competency (id) on delete set null;
