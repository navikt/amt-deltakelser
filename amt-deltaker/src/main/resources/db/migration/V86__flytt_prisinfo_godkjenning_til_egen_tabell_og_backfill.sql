-- Godkjenningsinformasjon (hvem og hvilken enhet som godkjente prisinfo) flyttes fra
-- enkeltplass_prisinformasjon til en egen tabell der feltene er påkrevd. Dette gjør
-- koden ryddigere, siden vi slipper å la feltene være null for bestemte statuser.
CREATE TABLE enkeltplass_prisinfo_godkjenning (
    prisinformasjon_id UUID PRIMARY KEY REFERENCES enkeltplass_prisinformasjon (id),
    godkjent_av UUID NOT NULL REFERENCES nav_ansatt (id),
    godkjent_av_enhet UUID NOT NULL REFERENCES nav_enhet (id)
);

-- Backfill inn i ny tabell.
-- Prioriterer eksisterende verdier på prisinfo-raden, og faller tilbake til vedtak når de mangler.
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
INSERT INTO enkeltplass_prisinfo_godkjenning (prisinformasjon_id, godkjent_av, godkjent_av_enhet)
SELECT
    prisinfo.id,
    COALESCE(prisinfo.godkjent_av, vedtak_kilde.opprettet_av),
    COALESCE(prisinfo.godkjent_av_enhet, vedtak_kilde.opprettet_av_enhet)
FROM enkeltplass_prisinformasjon prisinfo
LEFT JOIN vedtak_kilde ON vedtak_kilde.deltakerliste_id = prisinfo.deltakerliste_id
WHERE prisinfo.status = 'GODKJENT'
  AND COALESCE(prisinfo.godkjent_av, vedtak_kilde.opprettet_av) IS NOT NULL
  AND COALESCE(prisinfo.godkjent_av_enhet, vedtak_kilde.opprettet_av_enhet) IS NOT NULL;

ALTER TABLE enkeltplass_prisinformasjon
    DROP COLUMN godkjent_av,
    DROP COLUMN godkjent_av_enhet;
