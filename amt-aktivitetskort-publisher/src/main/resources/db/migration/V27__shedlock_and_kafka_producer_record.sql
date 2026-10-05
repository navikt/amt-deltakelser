CREATE SEQUENCE kafka_producer_record_id_seq;

CREATE TABLE kafka_producer_record (
    id BIGINT NOT NULL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    key BYTEA,
    value BYTEA,
    headers_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE shedlock
(
    name       VARCHAR(128) PRIMARY KEY,
    lock_until TIMESTAMP(3) NULL,
    locked_at  TIMESTAMP(3) NULL,
    locked_by  VARCHAR(255)
);
