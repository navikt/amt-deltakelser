CREATE INDEX IF NOT EXISTS deltaker_status_active_type_idx
    ON deltaker_status (deltaker_id, type, gyldig_fra)
    WHERE gyldig_til IS NULL;

DROP INDEX IF EXISTS deltaker_status_ikke_sluttet_idx;