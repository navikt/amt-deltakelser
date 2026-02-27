CREATE TABLE vurdering
(
    id                              uuid primary key,
    deltaker_id                     uuid                     not null references deltaker (id),
    opprettet_av_arrangor_ansatt_id uuid                     not null,
    opprettet                       timestamp with time zone not null,
    vurderingstype                  varchar                  not null,
    begrunnelse                     varchar,
    modified_at                     timestamp with time zone,
    created_at                      timestamp with time zone
);