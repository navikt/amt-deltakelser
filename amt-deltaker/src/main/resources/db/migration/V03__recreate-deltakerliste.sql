DROP TABLE deltakerliste;

CREATE TABLE deltakerliste
(
    id             uuid PRIMARY KEY,
    navn           varchar                          not null,
    status         varchar                          not null,
    arrangor_id    uuid references arrangor (id)    not null,
    tiltakstype_id uuid references tiltakstype (id) not null,
    start_dato     date,
    slutt_dato     date,
    oppstart       varchar,
    modified_at    timestamp with time zone         not null default current_timestamp,
    created_at     timestamp with time zone         not null default current_timestamp
);