ALTER TABLE enkeltplass_prisinformasjon
    ADD COLUMN IF NOT EXISTS begrunnelse VARCHAR;

-- Begrunnelsen har til nå kun ligget i endringshistorikken (deltaker_endring).
-- DISTINCT ON sørger for én rad per prisinformasjon (nyeste endring) dersom historikken har duplikater.
UPDATE enkeltplass_prisinformasjon prisinfo
SET begrunnelse = endring.begrunnelse
FROM (
    SELECT DISTINCT ON ((deltaker_endring.endring ->> 'prisinformasjonId')::uuid)
        (deltaker_endring.endring ->> 'prisinformasjonId')::uuid AS prisinformasjon_id,
        deltaker_endring.endring ->> 'begrunnelse' AS begrunnelse
    FROM deltaker_endring
    WHERE deltaker_endring.endring ->> 'type' = 'EndrePrisinfo'
      AND deltaker_endring.endring ->> 'prisinformasjonId' IS NOT NULL
      AND deltaker_endring.endring ->> 'begrunnelse' IS NOT NULL
    ORDER BY (deltaker_endring.endring ->> 'prisinformasjonId')::uuid, deltaker_endring.created_at DESC
) endring
WHERE prisinfo.id = endring.prisinformasjon_id;
