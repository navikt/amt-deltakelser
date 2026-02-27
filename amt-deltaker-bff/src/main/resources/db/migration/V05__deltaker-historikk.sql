create table deltaker_historikk
(
    id           uuid                                               not null primary key,
    deltaker_id  uuid references deltaker,
    endringstype varchar                                            not null,
    endring      jsonb                                              not null,
    endret_av    varchar                                            not null,
    created_at   timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at  timestamp with time zone default CURRENT_TIMESTAMP not null
);
