CREATE TABLE deltakerliste_sertifisering_valg (
    deltakerliste_id UUID NOT NULL REFERENCES deltakerliste(id) ON DELETE CASCADE,
    sertifisering_id INT NOT NULL,
    sertifisering_navn VARCHAR NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    PRIMARY KEY (deltakerliste_id, sertifisering_id)
);

