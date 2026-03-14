CREATE TABLE deltakerliste
(
    id          uuid PRIMARY KEY,
    navn        varchar                  not null,
    status      varchar                  not null,
    arrangor_id uuid                     not null,
    tiltak_navn varchar                  not null,
    tiltak_type varchar                  not null,
    start_dato  date,
    slutt_dato  date,
    modified_at timestamp with time zone not null default current_timestamp
);