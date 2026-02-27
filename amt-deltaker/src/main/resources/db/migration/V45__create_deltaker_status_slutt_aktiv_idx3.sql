DROP INDEX IF EXISTS deltaker_status_slutt_aktiv_idx2;

CREATE INDEX IF NOT EXISTS deltaker_status_slutt_aktiv_idx3
    ON deltaker_status (deltaker_id, gyldig_fra)
    INCLUDE (id, type, aarsak, gyldig_til, created_at, modified_at)
    WHERE gyldig_til IS NULL
        AND type IN ('AVBRUTT', 'FULLFORT', 'HAR_SLUTTET');