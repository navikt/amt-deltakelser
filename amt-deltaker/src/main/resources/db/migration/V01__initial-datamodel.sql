CREATE TABLE arrangor
(
    id                     uuid PRIMARY KEY,
    navn                   varchar                  not null,
    organisasjonsnummer    varchar                  not null UNIQUE,
    overordnet_arrangor_id uuid,
    created_at             timestamp with time zone not null default current_timestamp,
    modified_at            timestamp with time zone not null default current_timestamp
);

CREATE TABLE deltakerliste
(
    id          uuid PRIMARY KEY,
    navn        varchar                       not null,
    status      varchar                       not null,
    arrangor_id uuid references arrangor (id) not null,
    tiltaksnavn varchar                       not null,
    tiltakstype varchar                       not null,
    start_dato  date,
    slutt_dato  date,
    oppstart    varchar,
    modified_at timestamp with time zone      not null default current_timestamp,
    created_at  timestamp with time zone      not null default current_timestamp
);

CREATE TABLE nav_ansatt
(
    id          uuid primary key,
    nav_ident   varchar                  not null,
    navn        varchar                  not null,
    created_at  timestamp with time zone not null default current_timestamp,
    modified_at timestamp with time zone not null default current_timestamp,
    unique (nav_ident)
);

create index nav_ansatt_nav_ident_idx on nav_ansatt (nav_ident);

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

create table nav_bruker
(
    person_id    uuid primary key                                   not null,
    personident varchar                                            not null,
    fornavn     varchar                                            not null,
    mellomnavn  varchar,
    etternavn   varchar                                            not null,
    created_at  timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index nav_bruker_personident_idx on nav_bruker (personident);