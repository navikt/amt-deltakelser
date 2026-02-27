CREATE TABLE vurdering
(
    id                              uuid primary key,
    deltaker_id                     uuid                     not null references deltaker (id),
    opprettet_av_arrangor_ansatt_id uuid                     not null,
    vurderingstype                  varchar                  not null,
    begrunnelse                     varchar,
    gyldig_fra                      timestamp with time zone not null,
    created_at                      timestamp with time zone not null default current_timestamp
);