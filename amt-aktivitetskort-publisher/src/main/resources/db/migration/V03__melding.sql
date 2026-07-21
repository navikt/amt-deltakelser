CREATE TABLE melding
(
    deltaker_id      uuid PRIMARY KEY         NOT NULL,
    deltakerliste_id uuid                     NOT NULL references deltakerliste (id),
    arrangor_id      uuid                     NOT NULL references arrangor (id),
    melding          jsonb                    NOT NULL,
    created_at       timestamp with time zone not null default current_timestamp,
    modified_at      timestamp with time zone not null default current_timestamp
)
