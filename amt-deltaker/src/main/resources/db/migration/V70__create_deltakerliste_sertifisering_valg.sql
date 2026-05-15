CREATE TABLE IF NOT EXISTS deltakerliste_sertifisering_valg (
    deltakerliste_id UUID NOT NULL REFERENCES deltakerliste(id) ON DELETE CASCADE,
    sertifisering_id INT NOT NULL,
    sertifisering_navn VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id, sertifisering_id)
);

-- Legg til ON DELETE CASCADE på eksisterende FK for deltakerliste_kodeverk_valg (V69 manglet dette)
ALTER TABLE deltakerliste_kodeverk_valg
    DROP CONSTRAINT deltakerliste_kodeverk_valg_deltakerliste_id_fkey,
    ADD CONSTRAINT deltakerliste_kodeverk_valg_deltakerliste_id_fkey
        FOREIGN KEY (deltakerliste_id) REFERENCES deltakerliste(id) ON DELETE CASCADE;

