-- Backfill av manglende godkjent_av / godkjent_av_enhet for historiske GODKJENT-rader.
-- Verdiene hentes fra det relaterte vedtaket som fattet endringen.
WITH vedtak_kilde AS (
    SELECT DISTINCT ON (deltaker.deltakerliste_id)
        deltaker.deltakerliste_id,
        vedtak.opprettet_av,
        vedtak.opprettet_av_enhet
    FROM deltaker
        JOIN vedtak ON vedtak.deltaker_id = deltaker.id
    WHERE vedtak.fattet IS NOT NULL
    ORDER BY
        deltaker.deltakerliste_id,
        vedtak.fattet DESC,
        vedtak.modified_at DESC
)
UPDATE enkeltplass_prisinformasjon prisinfo
SET
    godkjent_av = COALESCE(prisinfo.godkjent_av, vedtak_kilde.opprettet_av),
    godkjent_av_enhet = COALESCE(prisinfo.godkjent_av_enhet, vedtak_kilde.opprettet_av_enhet)
FROM vedtak_kilde
WHERE
    vedtak_kilde.deltakerliste_id = prisinfo.deltakerliste_id
    AND prisinfo.status = 'GODKJENT'
    AND (prisinfo.godkjent_av IS NULL OR prisinfo.godkjent_av_enhet IS NULL);


