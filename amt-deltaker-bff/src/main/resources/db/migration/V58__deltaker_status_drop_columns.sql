DELETE FROM deltaker_status WHERE gyldig_til IS NOT NULL;

DROP INDEX IF EXISTS deltaker_status_deltaker_gyldig_til_null_idx2;

ALTER TABLE deltaker_status DROP COLUMN IF EXISTS gyldig_til;
ALTER TABLE deltaker_status DROP COLUMN IF EXISTS modified_at;

CREATE UNIQUE INDEX IF NOT EXISTS deltaker_status_deltaker_id_unique_idx
    ON deltaker_status (deltaker_id)
    INCLUDE (type);
