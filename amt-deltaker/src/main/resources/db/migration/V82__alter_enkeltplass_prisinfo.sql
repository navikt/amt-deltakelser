ALTER TABLE enkeltplass_prisinformasjon
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'SENDT',
    ADD CONSTRAINT enkeltplass_prisinformasjon_status_check
        CHECK (status IN ('SENDT', 'RETURNERT', 'TIL_BEHANDLING', 'SATT_PA_VENT', 'GODKJENT'));

UPDATE enkeltplass_prisinformasjon
SET status = CASE WHEN okonomi_godkjent = TRUE THEN 'GODKJENT' ELSE 'TIL_BEHANDLING' END;

-- Mellomlagringstabell: kobler deltakerliste til current/pending prisinfo
CREATE TABLE IF NOT EXISTS deltakerliste_2_prisinformasjon
(
    deltakerliste_id   UUID                     NOT NULL REFERENCES deltakerliste (id),
    prisinformasjon_id UUID                     NOT NULL REFERENCES enkeltplass_prisinformasjon (id),
    rolle              VARCHAR(10)              NOT NULL DEFAULT 'ENDRING',
    modified_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id, rolle),
    UNIQUE (prisinformasjon_id, deltakerliste_id),
    CHECK (rolle IN ('GJELDENDE', 'ENDRING'))
);

-- Populer med eksisterende prisinfo som GJELDENDE
INSERT INTO deltakerliste_2_prisinformasjon (deltakerliste_id, prisinformasjon_id, rolle)
SELECT
    deltakerliste_id,
    id,
    CASE WHEN okonomi_godkjent = TRUE THEN 'GJELDENDE' ELSE 'ENDRING' END
FROM enkeltplass_prisinformasjon;

ALTER TABLE enkeltplass_prisinformasjon DROP COLUMN deltakerliste_id;
ALTER TABLE enkeltplass_prisinformasjon DROP COLUMN okonomi_godkjent;
