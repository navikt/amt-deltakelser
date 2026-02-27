create table hendelse
(
    id          uuid primary key,
    deltaker_id uuid                                               not null,
    deltaker    jsonb                                              not null,
    ansvarlig   jsonb                                              not null,
    payload     jsonb                                              not null,
    created_at  timestamp with time zone default current_timestamp not null
);