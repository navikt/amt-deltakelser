drop table vedtak;
drop table deltaker_endring;

alter table deltaker add column historikk jsonb not null default '[]'::jsonb;