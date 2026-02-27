alter table nav_bruker
    add column telefonnummer varchar,
    add column epost varchar,
    add column adresse jsonb,
    add column adressebeskyttelse varchar,
    add column er_skjermet boolean not null default false,
    add column nav_veileder_id uuid references nav_ansatt (id),
    add column nav_enhet_id uuid references nav_enhet (id);