CREATE TABLE innsok_paa_felles_oppstart
(
    id                            uuid primary key,
    deltaker_id                   uuid                     not null references deltaker (id),
    innsokt                       timestamp,
    innsokt_av                    uuid                     not null references nav_ansatt (id),
    innsokt_av_enhet              uuid                     not null references nav_enhet (id),
    deltakelsesinnhold_ved_innsok jsonb,
    utkast_delt                   timestamp,
    utkast_godkjent_av_nav        boolean                  not null,
    created_at                    timestamp with time zone not null default current_timestamp,
    modified_at                   timestamp with time zone not null default current_timestamp
);

