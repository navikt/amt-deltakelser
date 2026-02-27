CREATE INDEX IF NOT EXISTS deltaker_status_join_deltaker_idx2
    ON deltaker_status (deltaker_id, gyldig_fra)
    INCLUDE (type)
    WHERE gyldig_til IS NULL;

CREATE INDEX IF NOT EXISTS deltaker_status_ikke_sluttet_idx
    ON deltaker_status (deltaker_id, gyldig_fra)
    WHERE
        gyldig_til IS NULL
        AND type NOT IN ('HAR_SLUTTET','IKKE_AKTUELL','FEILREGISTRERT','AVBRUTT','FULLFORT','AVBRUTT_UTKAST');

CREATE INDEX IF NOT EXISTS deltakerliste_avsluttet_idx2
    ON deltakerliste (status)
    WHERE status IN ('AVSLUTTET', 'AVBRUTT', 'AVLYST');

DROP INDEX IF EXISTS deltaker_status_join_deltaker_idx;
DROP INDEX IF EXISTS deltakerliste_avsluttet_idx;