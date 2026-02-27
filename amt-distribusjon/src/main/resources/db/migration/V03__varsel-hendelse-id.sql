alter table varsel add column hendelse_id uuid;

update varsel
set hendelse_id = gen_random_uuid()
where hendelse_id is null;

alter table varsel alter column hendelse_id set not null;