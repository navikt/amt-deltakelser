create table ulest_hendelse
(
    id                uuid                                               not null primary key,
    deltaker_id       uuid                                               not null,
    opprettet         timestamp with time zone                           not null,
    ansvarlig         jsonb                                              not null,
    hendelse          jsonb                                              not null,
    created_at        timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at       timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index ulest_hendelse_deltaker_id_idx on ulest_hendelse (deltaker_id);