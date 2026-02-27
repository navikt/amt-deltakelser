DROP INDEX IF EXISTS deltaker_status_gyldig_til_idx;
DROP INDEX IF EXISTS deltaker_status_deltaker_gyldig_til_null_idx;

CREATE UNIQUE INDEX IF NOT EXISTS deltaker_status_deltaker_gyldig_til_null_idx2
    ON deltaker_status (deltaker_id)
    INCLUDE (type)
    WHERE gyldig_til IS NULL;