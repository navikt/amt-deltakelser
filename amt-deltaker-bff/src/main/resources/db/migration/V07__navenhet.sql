create table nav_enhet
(
    id               uuid primary key,
    nav_enhet_nummer varchar                  not null,
    navn             varchar                  not null,
    created_at       timestamp with time zone not null default current_timestamp,
    modified_at      timestamp with time zone not null default current_timestamp,
    unique (nav_enhet_nummer)
);

create index nav_enhet_nav_enhet_nr_idx on nav_enhet (nav_enhet_nummer);