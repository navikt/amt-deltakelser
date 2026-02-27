CREATE TABLE IF NOT EXISTS outbox_record
(
    id            SERIAL PRIMARY KEY,
    key           VARCHAR(255) NOT NULL,
    value         JSONB        NOT NULL,
    value_type    VARCHAR(255) NOT NULL,
    topic         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    modified_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    processed_at  TIMESTAMPTZ,
    status        VARCHAR(50)  NOT NULL DEFAULT 'PENDING',
    retry_count   INT          NOT NULL DEFAULT 0,
    retried_at    TIMESTAMPTZ,
    error_message TEXT
);

CREATE INDEX IF NOT EXISTS outbox_status_created_idx ON outbox_record (status, created_at);
