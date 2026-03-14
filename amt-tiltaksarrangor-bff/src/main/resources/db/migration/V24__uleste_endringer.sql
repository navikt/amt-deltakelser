create table ulest_endring
(
    id                              uuid primary key,
    deltaker_id                     uuid references deltaker                           not null,
    oppdatering                     jsonb                                              not null,
    created_at                      timestamp with time zone default current_timestamp not null,
    modified_at                     timestamp with time zone default current_timestamp not null
);
