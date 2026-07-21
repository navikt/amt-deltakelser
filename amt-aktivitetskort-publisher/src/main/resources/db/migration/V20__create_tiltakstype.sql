CREATE TABLE tiltakstype
(
    id          UUID PRIMARY KEY,
    navn        VARCHAR                  not null,
    tiltakskode VARCHAR                  not null,
    created_at  timestamp with time zone not null default CURRENT_TIMESTAMP,
    modified_at timestamp with time zone not null default CURRENT_TIMESTAMP,
    UNIQUE (tiltakskode)
);
