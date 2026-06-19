CREATE TABLE IF NOT EXISTS opplaering_kategorisering_valg
(
    deltakerliste_id UUID                     NOT NULL REFERENCES deltakerliste (id) ON DELETE CASCADE,
    representerer    VARCHAR(20)              NOT NULL,
    kodeverk_id      UUID                     NOT NULL,
    tekst            TEXT                     NOT NULL,
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id, representerer, kodeverk_id)
);

DROP TABLE IF EXISTS deltakerliste_kodeverk_valg;