-- Composite index for "siste vurdering per deltaker"-oppslag i TiltakskoordinatorViewRepository.
-- Erstatter sekvensiell sort per deltaker-rad med en ren index backward scan (LIMIT 1).
CREATE INDEX IF NOT EXISTS vurdering_deltaker_id_gyldig_fra_desc_idx
    ON vurdering (deltaker_id, gyldig_fra DESC);

