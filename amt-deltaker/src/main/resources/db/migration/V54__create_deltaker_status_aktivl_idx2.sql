-- SELECT * FROM pg_stat_user_indexes WHERE indexrelname = 'vedtak_fattet_idx';
DROP INDEX IF EXISTS vedtak_fattet_idx;

-- SELECT * FROM pg_stat_user_indexes WHERE indexrelname = 'vedtak_gyldig_til_idx';
DROP INDEX IF EXISTS vedtak_gyldig_til_idx;

DROP INDEX IF EXISTS deltaker_status_aktiv_idx;

CREATE INDEX IF NOT EXISTS deltaker_status_aktiv_idx2
    ON deltaker_status (deltaker_id)
    INCLUDE (type, gyldig_fra)
    WHERE
        gyldig_til IS NULL
        AND type NOT IN ('HAR_SLUTTET','IKKE_AKTUELL','FEILREGISTRERT','AVBRUTT','FULLFORT','AVBRUTT_UTKAST');

CREATE INDEX IF NOT EXISTS vedtak_aktiv_idx
    ON vedtak(deltaker_id)
    WHERE gyldig_til IS NULL;