DROP INDEX IF EXISTS deltaker_status_gyldig_tilfra_idx;

CREATE INDEX IF NOT EXISTS deltaker_status_deltar_aktiv_idx
    ON deltaker_status (deltaker_id)
    WHERE
        gyldig_til IS NULL
            AND type = 'DELTAR';

CREATE INDEX IF NOT EXISTS deltaker_status_slutt_aktiv_idx
    ON deltaker_status (deltaker_id, gyldig_fra)
    WHERE gyldig_til IS NULL
        AND type IN ('AVBRUTT', 'FULLFORT', 'HAR_SLUTTET');