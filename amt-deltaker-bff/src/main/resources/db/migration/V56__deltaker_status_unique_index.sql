DROP INDEX IF EXISTS deltaker_status_deltaker_id_idx;

ALTER TABLE deltaker_status ALTER COLUMN deltaker_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS deltaker_status_deltaker_gyldig_til_null_idx
    ON deltaker_status (deltaker_id)
    WHERE gyldig_til IS NULL;