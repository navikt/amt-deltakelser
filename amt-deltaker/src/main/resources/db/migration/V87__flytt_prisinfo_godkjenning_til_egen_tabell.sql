-- Godkjenningsinformasjon (hvem og hvilken enhet som godkjente prisinfo) flyttes fra
-- enkeltplass_prisinformasjon til en egen tabell der feltene er påkrevd.
-- En rad her betyr at godkjenneren er kjent. Manglende rad betyr at godkjenneren ikke er
-- kjent (f.eks. historiske data uten registrert saksbehandler) - det krever ingen falske
-- placeholder-verdier.
CREATE TABLE enkeltplass_prisinfo_godkjenning (
    prisinformasjon_id UUID PRIMARY KEY REFERENCES enkeltplass_prisinformasjon (id),
    godkjent_av         UUID NOT NULL REFERENCES nav_ansatt (id),
    godkjent_av_enhet   UUID NOT NULL REFERENCES nav_enhet (id)
);

-- Flytt eksisterende godkjenningsdata - kun rader der begge felter faktisk er satt fra før.
INSERT INTO enkeltplass_prisinfo_godkjenning (prisinformasjon_id, godkjent_av, godkjent_av_enhet)
SELECT id, godkjent_av, godkjent_av_enhet
FROM enkeltplass_prisinformasjon
WHERE godkjent_av IS NOT NULL AND godkjent_av_enhet IS NOT NULL;

-- Dropper kolonnene (og indeksene fra V85 som ligger på dem).
ALTER TABLE enkeltplass_prisinformasjon
    DROP COLUMN godkjent_av,
    DROP COLUMN godkjent_av_enhet;

