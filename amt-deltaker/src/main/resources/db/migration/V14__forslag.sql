create table forslag
(
    id                uuid                                               not null primary key,
    deltaker_id       uuid references deltaker,
    arrangoransatt_id uuid                                               not null,
    opprettet         timestamp with time zone                           not null,
    begrunnelse       varchar                                            not null,
    endring           jsonb                                              not null,
    status            jsonb                                              not null,
    created_at        timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at       timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index forslag_deltaker_id_idx on forslag (deltaker_id);