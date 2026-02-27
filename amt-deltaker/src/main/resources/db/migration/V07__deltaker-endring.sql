create table deltaker_endring
(
    id              uuid                                               not null primary key,
    deltaker_id     uuid references deltaker,
    endring         jsonb                                              not null,
    endret_av       uuid references nav_ansatt (id)                    not null,
    endret_av_enhet uuid references nav_enhet (id)                     not null,
    created_at      timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at     timestamp with time zone default CURRENT_TIMESTAMP not null
);