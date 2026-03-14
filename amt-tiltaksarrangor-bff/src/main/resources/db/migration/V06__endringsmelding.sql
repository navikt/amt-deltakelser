CREATE TABLE endringsmelding
(
    id          UUID PRIMARY KEY,
    deltaker_id UUID                     NOT NULL,
    type        VARCHAR                  NOT NULL,
    innhold     JSONB,
    modified_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);