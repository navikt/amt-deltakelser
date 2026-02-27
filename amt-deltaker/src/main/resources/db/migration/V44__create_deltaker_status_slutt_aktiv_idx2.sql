DROP INDEX IF EXISTS deltaker_status_deltaker_id_idx;

DROP INDEX IF EXISTS deltaker_status_slutt_aktiv_idx;

CREATE INDEX IF NOT EXISTS deltaker_status_slutt_aktiv_idx2
    ON deltaker_status (gyldig_fra, deltaker_id)
    INCLUDE (id, type, aarsak, gyldig_til, created_at, modified_at)
    WHERE gyldig_til IS NULL
        AND type IN ('AVBRUTT','FULLFORT','HAR_SLUTTET');