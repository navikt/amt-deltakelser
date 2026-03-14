create table forslag
(
    id                              uuid primary key,
    deltaker_id                     uuid references deltaker                           not null,
    opprettet_av_arrangor_ansatt_id uuid references ansatt                             not null,
    begrunnelse                     varchar                                            not null,
    endring                         jsonb                                              not null,
    status                          jsonb                                              not null,
    created_at                      timestamp with time zone default current_timestamp not null,
    modified_at                     timestamp with time zone default current_timestamp not null
);
