CREATE TABLE outbox_record
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

CREATE INDEX idx_outbox_status_created ON outbox_record (status, created_at);
