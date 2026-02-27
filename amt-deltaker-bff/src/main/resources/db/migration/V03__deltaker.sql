create table deltaker
(
    id                   uuid                                               not null primary key,
    personident          varchar                                            not null,
    deltakerliste_id     uuid                                               not null references deltakerliste (id),
    startdato            date,
    sluttdato            date,
    dager_per_uke        double precision,
    deltakelsesprosent   double precision,
    bakgrunnsinformasjon text,
    mal                  jsonb                                              not null,
    sist_endret_av       varchar                                            not null,
    created_at           timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at          timestamp with time zone default CURRENT_TIMESTAMP not null
);

create table deltaker_status
(
    id          uuid                                               not null primary key,
    deltaker_id uuid references deltaker,
    type        varchar                                            not null,
    aarsak      varchar,
    gyldig_fra  timestamp with time zone                           not null,
    gyldig_til  timestamp with time zone,
    created_at  timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at timestamp with time zone default CURRENT_TIMESTAMP not null
);
