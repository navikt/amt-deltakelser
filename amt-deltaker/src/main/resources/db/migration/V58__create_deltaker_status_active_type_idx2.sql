CREATE INDEX IF NOT EXISTS deltaker_status_type_active_idx2
    ON deltaker_status (type, gyldig_fra, deltaker_id)
    INCLUDE (aarsak, created_at, modified_at)
    WHERE gyldig_til IS NULL;

DROP INDEX IF EXISTS deltaker_status_active_type_idx;