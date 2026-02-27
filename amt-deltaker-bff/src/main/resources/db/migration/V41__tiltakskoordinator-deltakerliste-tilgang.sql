create table tiltakskoordinator_deltakerliste_tilgang
(
    id               uuid primary key,
    nav_ansatt_id    uuid references nav_ansatt (id)    not null,
    deltakerliste_id uuid references deltakerliste (id) not null,
    gyldig_fra       timestamp with time zone           not null,
    gyldig_til       timestamp with time zone,
    created_at       timestamp with time zone default current_timestamp,
    modified_at      timestamp with time zone default current_timestamp
);

create index nav_ansatt_id_idx on tiltakskoordinator_deltakerliste_tilgang (nav_ansatt_id);
