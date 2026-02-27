

CREATE INDEX IF NOT EXISTS deltaker_status_join_deltaker_idx
    ON deltaker_status (deltaker_id, gyldig_fra)
    INCLUDE (type)
    WHERE gyldig_til IS NULL;

DROP INDEX IF EXISTS deltaker_status_aktiv_idx2;
DROP INDEX IF EXISTS deltaker_status_gyldig_til_null_idx;