CREATE TABLE endring_fra_arrangor_behandlet (
    id UUID PRIMARY KEY,
    deltaker_id UUID REFERENCES deltaker (id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX endring_fra_arrangor_behandlet_deltaker_id_idx
    ON endring_fra_arrangor_behandlet (deltaker_id);

-- Treat existing history rows as already processed.
INSERT INTO endring_fra_arrangor_behandlet (id, deltaker_id)
SELECT id, deltaker_id
FROM endring_fra_arrangor;

-- Rollback: dropping this table removes replay protection for messages processed after this migration.
