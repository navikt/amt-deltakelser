alter table varsel add column hendelser uuid[] not null default array[]::uuid[];

update varsel set hendelser = array[hendelse_id];

alter table varsel drop column hendelse_id;
