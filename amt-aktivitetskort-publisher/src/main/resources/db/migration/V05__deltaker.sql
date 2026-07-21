CREATE TABLE deltaker
(
    id                    uuid primary key         not null,
    personident           varchar                  not null,
    deltakerliste_id      uuid                     not null references deltakerliste (id),
    deltaker_status_type  varchar                  not null,
    deltaker_status_arsak varchar,
    dager_per_uke         int,
    prosent_stilling      double precision,
    start_dato            date,
    slutt_dato            date,
    deltar_pa_kurs        boolean,
    created_at            timestamp with time zone not null default current_timestamp,
    modified_at           timestamp with time zone not null default current_timestamp
)
