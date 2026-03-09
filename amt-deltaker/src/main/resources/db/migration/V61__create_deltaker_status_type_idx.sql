CREATE INDEX IF NOT EXISTS deltaker_status_type_idx
    ON deltaker_status (type, deltaker_id, gyldig_fra)
    WHERE gyldig_til IS NULL;