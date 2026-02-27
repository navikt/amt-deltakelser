create table endring_fra_tiltakskoordinator
(
    id            uuid                                               not null primary key,
    deltaker_id   uuid references deltaker,
    nav_ansatt_id uuid                                               not null,
    endret        timestamp with time zone                           not null,
    endring       jsonb                                              not null,
    created_at    timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at   timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index endring_fra_tiltaksoordinator_deltaker_id_idx on endring_fra_tiltakskoordinator (deltaker_id);