alter table varsel add column er_sendt bool not null default true;
alter table varsel alter column er_sendt drop default;
