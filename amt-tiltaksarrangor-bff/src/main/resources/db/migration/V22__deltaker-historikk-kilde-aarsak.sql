alter table deltaker add column historikk jsonb not null default '[]'::jsonb;

alter table deltaker add column kilde varchar not null default 'ARENA';

alter table deltaker add column aarsak jsonb;