create table endringsmelding
(
    id                              uuid primary key                                   not null,
    deltaker_id                     uuid references deltaker                           not null,
    utfort_av_nav_ansatt_id         uuid references nav_ansatt,
    opprettet_av_arrangor_ansatt_id uuid                                               not null,
    utfort_tidspunkt                timestamp with time zone,
    status                          varchar                                            not null,
    type                            varchar                                            not null,
    innhold                         jsonb,
    created_at                      timestamp with time zone default CURRENT_TIMESTAMP not null,
    modified_at                     timestamp with time zone default CURRENT_TIMESTAMP not null
);

create index endringsmelding_deltaker_id on endringsmelding (deltaker_id);
