DROP INDEX IF EXISTS deltaker_status_slutt_aktiv_idx3;

CREATE INDEX IF NOT EXISTS deltaker_status_slutt_aktiv_idx
    ON deltaker_status (deltaker_id)
    INCLUDE (gyldig_fra, type, gyldig_til, id, aarsak, created_at, modified_at)
    WHERE gyldig_til IS NULL
        AND type IN ('AVBRUTT','FULLFORT','HAR_SLUTTET');