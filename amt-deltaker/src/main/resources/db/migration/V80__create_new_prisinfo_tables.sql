CREATE TABLE IF NOT EXISTS enkeltplass_prisinformasjon
(
    id                        UUID                     NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    deltakerliste_id          UUID                     NOT NULL REFERENCES deltakerliste (id) ON DELETE CASCADE,
    okonomi_godkjent          BOOLEAN                  NOT NULL             DEFAULT FALSE,
    prisinformasjon_json_type VARCHAR                  NOT NULL,

    -- Anskaffelse
    anskaffelse_pris          INT,

    -- Tilskudd/IngenKostnader
    tilleggsopplysninger      VARCHAR,

    -- IngenKostnader
    ingenkostnader_aarsak     VARCHAR,

    modified_at               TIMESTAMP WITH TIME ZONE NOT NULL             DEFAULT NOW(),
    created_at                TIMESTAMP WITH TIME ZONE NOT NULL             DEFAULT NOW(),
    unique (deltakerliste_id, okonomi_godkjent)
);

CREATE TABLE IF NOT EXISTS enkeltplass_prisinformasjon_belop
(
    prisinfo_id UUID                     NOT NULL REFERENCES enkeltplass_prisinformasjon (id) ON DELETE CASCADE,
    pristype    VARCHAR                  NOT NULL,
    pris        INT                      NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (prisinfo_id, pristype)
);

-- Populer enkeltplass_prisinformasjon fra deltakerliste_prisinfo.
-- okonomi_godkjent = true dersom deltakerlisten har deltakere med aktiv status.
INSERT INTO enkeltplass_prisinformasjon (id, deltakerliste_id, okonomi_godkjent, prisinformasjon_json_type,
                                         anskaffelse_pris, tilleggsopplysninger, ingenkostnader_aarsak,
                                         modified_at, created_at)
SELECT deltaker.id, -- denne har blitt benyttet for totrinnskontroll tidligere
       deltakerliste_prisinfo.deltakerliste_id,
       EXISTS (SELECT 1
               FROM deltakerliste d
                        JOIN deltaker dk ON d.id = dk.deltakerliste_id
                        JOIN deltaker_status ds ON dk.id = ds.deltaker_id
                   AND ds.type IN ('VENTER_PA_OPPSTART', 'DELTAR', 'FULLFORT')
                   AND ds.gyldig_til IS NULL
               WHERE d.id = deltakerliste_prisinfo.deltakerliste_id),
       deltakerliste_prisinfo.prisinformasjon_json_type,
       deltakerliste_prisinfo.anskaffelse_pris,
       deltakerliste_prisinfo.tilleggsopplysninger,
       deltakerliste_prisinfo.ingenkostnader_aarsak,
       deltakerliste_prisinfo.modified_at,
       deltakerliste_prisinfo.created_at
FROM deltakerliste_prisinfo
         JOIN deltaker on deltakerliste_prisinfo.deltakerliste_id = deltaker.deltakerliste_id;

-- Populer enkeltplass_prisinformasjon_belop fra deltakerliste_prisinfo_belop.
INSERT INTO enkeltplass_prisinformasjon_belop (prisinfo_id, pristype, pris, created_at)
SELECT enkeltplass_prisinformasjon.id,
       deltakerliste_prisinfo_belop.pristype,
       deltakerliste_prisinfo_belop.pris,
       deltakerliste_prisinfo_belop.created_at
FROM deltakerliste_prisinfo_belop
         JOIN enkeltplass_prisinformasjon
              ON deltakerliste_prisinfo_belop.deltakerliste_id = enkeltplass_prisinformasjon.deltakerliste_id;