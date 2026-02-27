alter table tiltakstype
    add column tiltakskode varchar not null default 'UKJENT',
    add column innsatsgrupper jsonb not null default '[]'::jsonb;