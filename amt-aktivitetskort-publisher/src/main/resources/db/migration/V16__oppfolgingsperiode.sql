CREATE TABLE oppfolgingsperiode
(
    id          UUID PRIMARY KEY,
    start_dato  timestamp NOT NULL,
    slutt_dato  timestamp,
    created_at  timestamp not null default current_timestamp,
    modified_at  timestamp not null default current_timestamp
)
