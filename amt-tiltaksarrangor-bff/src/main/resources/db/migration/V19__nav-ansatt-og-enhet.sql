CREATE TABLE nav_ansatt
(
    id          uuid primary key,
    nav_ident   varchar                  not null,
    navn        varchar                  not null,
    telefon     varchar,
    epost       varchar,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp,
    unique (nav_ident)
);

create table nav_enhet
(
    id               uuid primary key,
    nav_enhet_nummer varchar                  not null,
    navn             varchar                  not null,
    created_at       timestamp with time zone not null default current_timestamp,
    modified_at      timestamp with time zone not null default current_timestamp,
    unique (nav_enhet_nummer)
);
