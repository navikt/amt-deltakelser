CREATE TABLE ulest_hendelse (
    id uuid PRIMARY KEY,
    deltaker_id uuid NOT NULL,
    opprettet timestamp with time zone NOT NULL,
    ansvarlig jsonb,
    hendelse jsonb NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    modified_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX ulest_hendelse_deltaker_id_idx ON ulest_hendelse (deltaker_id);

-- Migration note:
-- The rows currently live in amt-deltaker-bff. Backfill this table from the old table
-- in a dedicated cutover step before switching the bff to read from amt-deltaker.
-- During the migration window, keep both consumers running so events are written to
-- both databases. After the bff reads have been moved, remove the old bff-owned table
-- and its consumer in a later migration.
