create table vedtak
(
    id                   uuid                                               not null primary key,
    deltaker_id          uuid references deltaker,
    fattet               timestamp with time zone,
    gyldig_til           timestamp with time zone,
    deltaker_ved_vedtak  jsonb                                              not null,
    fattet_av_nav        boolean                                            not null,
    opprettet_av         uuid references nav_ansatt (id)                    not null,
    opprettet_av_enhet   uuid references nav_enhet (id)                     not null,
    sist_endret_av       uuid references nav_ansatt (id)                    not null,
    sist_endret_av_enhet uuid references nav_enhet (id)                     not null,
    created_at           timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at          timestamp with time zone default CURRENT_TIMESTAMP not null
);