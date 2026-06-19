CREATE TABLE IF NOT EXISTS deltakerliste_prisinfo
(
    deltakerliste_id          UUID                     NOT NULL REFERENCES deltakerliste (id) ON DELETE CASCADE,
    prisinformasjon_json_type VARCHAR                  NOT NULL,

    -- Anskaffelse
    anskaffelse_pris          INT,

    -- Tilskudd/IngenKostnader
    tilleggsopplysninger      VARCHAR,

    -- IngenKostnader
    ingenkostnader_aarsak     VARCHAR,

    created_at                TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id)
);

CREATE TABLE IF NOT EXISTS deltakerliste_prisinfo_belop
(
    deltakerliste_id UUID                     NOT NULL REFERENCES deltakerliste (id) ON DELETE CASCADE,
    pristype         VARCHAR                  NOT NULL,
    pris             INT                      NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id, pristype)
);

-- patcher deltakerliste_prisinfo i dev
ALTER TABLE deltakerliste_prisinfo ADD COLUMN IF NOT EXISTS modified_at TIMESTAMP WITH TIME ZONE DEFAULT NOW();
UPDATE deltakerliste_prisinfo SET modified_at = created_at WHERE modified_at IS NULL;
ALTER TABLE deltakerliste_prisinfo ALTER COLUMN modified_at SET NOT NULL;