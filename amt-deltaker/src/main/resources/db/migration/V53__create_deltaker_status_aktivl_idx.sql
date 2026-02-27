CREATE INDEX IF NOT EXISTS deltaker_status_aktiv_idx
    ON deltaker_status (deltaker_id)
    WHERE
        gyldig_til IS NULL
        AND type NOT IN ('HAR_SLUTTET','IKKE_AKTUELL','FEILREGISTRERT','AVBRUTT','FULLFORT','AVBRUTT_UTKAST');