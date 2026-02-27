-- SELECT * FROM pg_stat_user_indexes WHERE indexrelname = 'deltaker_status_gyldig_tilfra_idx'; -- ikke i bruk
DROP INDEX IF EXISTS deltaker_status_gyldig_tilfra_idx; -- ikke i bruk
-- SELECT * FROM pg_stat_user_indexes WHERE indexrelname = 'deltakerliste_tiltakstype_id_idx'; -- ikke i bruk
DROP INDEX IF EXISTS deltakerliste_tiltakstype_id_idx; -- ikke i bruk

CREATE INDEX IF NOT EXISTS deltaker_status_gyldig_til_null_idx
    ON deltaker_status (deltaker_id)
    INCLUDE (type, gyldig_fra)
    WHERE gyldig_til IS NULL;

CREATE INDEX IF NOT EXISTS deltakerliste_avsluttet_idx
    ON deltakerliste (id)
    WHERE status IN ('AVSLUTTET', 'AVBRUTT', 'AVLYST');