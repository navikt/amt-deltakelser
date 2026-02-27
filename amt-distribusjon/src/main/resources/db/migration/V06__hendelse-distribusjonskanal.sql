alter table hendelse add column distribusjonskanal varchar not null default 'DITT_NAV';

alter table hendelse alter column distribusjonskanal drop default;