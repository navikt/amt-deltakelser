-- Funn 1: innsokt og utkast_delt er timestamp without time zone, resten av skjemaet bruker timestamptz.
-- Verdiene er lagret i Europe/Oslo (JVM TZ=Europe/Oslo via Dockerfile).
ALTER TABLE innsok_paa_felles_oppstart
    ALTER COLUMN innsokt TYPE timestamptz USING innsokt AT TIME ZONE 'Europe/Oslo',
    ALTER COLUMN utkast_delt TYPE timestamptz USING utkast_delt AT TIME ZONE 'Europe/Oslo';

-- Funn 21: Duplikate innsøkinger (retry-bug fra 2026-01-08). Behold nyeste per deltaker, slett resten.
DELETE FROM innsok_paa_felles_oppstart
WHERE id NOT IN (
    SELECT DISTINCT ON (deltaker_id) id
    FROM innsok_paa_felles_oppstart
    ORDER BY deltaker_id, innsokt DESC
);

DROP INDEX IF EXISTS innsok_paa_felles_oppstart_deltaker_id_idx;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'innsok_paa_felles_oppstart_deltaker_id_key'
    ) THEN
        ALTER TABLE innsok_paa_felles_oppstart
            ADD CONSTRAINT innsok_paa_felles_oppstart_deltaker_id_key UNIQUE (deltaker_id);
    END IF;
END $$;

-- Funn 3: personident (fnr/dnr) bør være UNIQUE. Verifisert: 0 duplikater.
-- Erstatter eksisterende non-unique indeks med UNIQUE constraint.
DROP INDEX IF EXISTS nav_bruker_personident_idx;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'nav_bruker_personident_key'
    ) THEN
        ALTER TABLE nav_bruker
            ADD CONSTRAINT nav_bruker_personident_key UNIQUE (personident);
    END IF;
END $$;

-- Funn 5: FK-kolonner deltaker_id er nullable men har NO ACTION delete rule.
-- Verifisert: ingen rader med NULL deltaker_id i noen av tabellene.
-- SET NOT NULL er idempotent i PostgreSQL — kjører uten feil selv om kolonnen allerede er NOT NULL.
ALTER TABLE deltaker_endring ALTER COLUMN deltaker_id SET NOT NULL;
ALTER TABLE deltaker_status ALTER COLUMN deltaker_id SET NOT NULL;
ALTER TABLE endring_fra_arrangor ALTER COLUMN deltaker_id SET NOT NULL;
ALTER TABLE endring_fra_tiltakskoordinator ALTER COLUMN deltaker_id SET NOT NULL;
ALTER TABLE forslag ALTER COLUMN deltaker_id SET NOT NULL;
ALTER TABLE vedtak ALTER COLUMN deltaker_id SET NOT NULL;

-- Funn 17+18: UNIQUE-indeksen dekker allerede oppslag, drop duplikate non-unique indekser.
DROP INDEX IF EXISTS nav_ansatt_nav_ident_idx;
DROP INDEX IF EXISTS nav_enhet_nav_enhet_nr_idx;

-- Funn 19: Typo i indeksnavn (mangler 'k' i tiltakskoordinator).
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_indexes WHERE indexname = 'endring_fra_tiltaksoordinator_deltaker_id_idx'
    ) THEN
        ALTER INDEX endring_fra_tiltaksoordinator_deltaker_id_idx
            RENAME TO endring_fra_tiltakskoordinator_deltaker_id_idx;
    END IF;
END $$;

-- Funn 20: Manglende indeks på FK-kolonne deltakerliste.tiltakstype_id.
CREATE INDEX IF NOT EXISTS deltakerliste_tiltakstype_id_idx
    ON deltakerliste USING btree (tiltakstype_id);

-- Funn 24: getUbehandletDeltakelsesmengder filtrerer på JSON-felt uten indeks (sequential scan på 195k rader).
CREATE INDEX IF NOT EXISTS deltaker_endring_endring_type_idx
    ON deltaker_endring ((endring->>'type'))
    WHERE behandlet IS NULL;

