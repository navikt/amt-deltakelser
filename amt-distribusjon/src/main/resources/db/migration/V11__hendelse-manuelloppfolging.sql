alter table hendelse add column manuelloppfolging boolean not null default false;

alter table hendelse alter column manuelloppfolging drop default;