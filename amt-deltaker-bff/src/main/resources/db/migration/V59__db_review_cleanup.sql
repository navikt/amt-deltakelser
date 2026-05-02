-- =========================================================================
-- V59__db_review_cleanup.sql
--
-- Endringer (12 stk):
--   Funn 1  — deltaker.kan_endres                                 NOT NULL
--   Funn 2  — forslag.deltaker_id                                 NOT NULL
--   Funn 3  — tiltakskoordinator_…tilgang.created_at/modified_at  NOT NULL
--   Funn 4  — vurdering.created_at/modified_at                    DEFAULT + NOT NULL
--   Funn 6  — nav_bruker.personident                              UNIQUE
--   Funn 8  — nav_bruker.nav_enhet_id                             CREATE INDEX
--   Funn 9  — nav_bruker.nav_veileder_id                          CREATE INDEX
--   Funn 10 — tiltakskoordinator_…tilgang.deltakerliste_id        CREATE INDEX
--   Funn 11 — nav_ansatt_nav_ident_idx                            DROP (redundant)
--   Funn 12 — nav_enhet_nav_enhet_nr_idx                          DROP (redundant)
--   Funn 13 — nav_bruker_personident_idx                          DROP (redundant)
--   Funn 15 — tiltakskoordinator_…tilgang                         CHECK gyldig_periode
--
-- Bevisst utelatt fra V59:
--   Funn 5  — arrangor.overordnet_arrangor_id FK
--             Risiko ved Kafka-rekkefølge i ArrangorConsumer (barn-arrangør
--             kan komme før parent).
--   Funn 7  — deltaker_status.deltaker_id UNIQUE
--             Allerede dekket av V56/V57 (deltaker_status_deltaker_id_unique_idx).
--   Funn 14 — arrangor.overordnet_arrangor_id CREATE INDEX
--             Indeksen ville kun dekke "finn underarrangører for gitt parent",
--             men ingen spørring i kodebasen gjør dette. JOIN-ene slår opp
--             arrangor.id (PK), ikke overordnet_arrangor_id.
--
-- NB: Migreringen kjører i én transaksjon (Flyway default). Forventet kort
-- skrive-nedetid på `nav_bruker` (~30s) og `deltaker` (~30s–noen min for
-- SET NOT NULL på 1.67M rader). CONCURRENTLY er bevisst utelatt.
-- =========================================================================


-- -------------------------------------------------------------------------
-- 1. Backfill — sett verdier på rader som har NULL der vi vil ha NOT NULL
-- -------------------------------------------------------------------------

-- Funn 1: defensiv — alle eksisterende rader skal ha kan_endres = true
UPDATE deltaker
   SET kan_endres = TRUE
 WHERE kan_endres IS NULL;

-- Funn 3: defensiv — created_at/modified_at har default men er nullable
-- (pre-flight viste 0 NULL-er, men vi sikrer oss likevel)
UPDATE tiltakskoordinator_deltakerliste_tilgang
   SET created_at  = COALESCE(created_at,  CURRENT_TIMESTAMP),
       modified_at = COALESCE(modified_at, CURRENT_TIMESTAMP)
 WHERE created_at IS NULL OR modified_at IS NULL;

-- Funn 4: backfill created_at/modified_at fra `opprettet` (NOT NULL).
-- For created_at: alle 4181 rader er NULL fordi V44 manglet både default og
-- VurderingRepository.upsert setter den ikke. Etter V59 fyller DB-default
-- inn ved nye INSERTs.
-- For modified_at: bare 16 rader er NULL (rader som aldri har vært gjennom
-- ON CONFLICT-stien).
UPDATE vurdering
   SET created_at  = COALESCE(created_at,  opprettet),
       modified_at = COALESCE(modified_at, opprettet)
 WHERE created_at IS NULL OR modified_at IS NULL;


-- -------------------------------------------------------------------------
-- 2. Defaults (Funn 4)
-- -------------------------------------------------------------------------

ALTER TABLE vurdering
    ALTER COLUMN created_at  SET DEFAULT CURRENT_TIMESTAMP,
    ALTER COLUMN modified_at SET DEFAULT CURRENT_TIMESTAMP;


-- -------------------------------------------------------------------------
-- 3. NOT NULL-endringer (Funn 1, 2, 3, 4)
-- -------------------------------------------------------------------------

ALTER TABLE deltaker
    ALTER COLUMN kan_endres SET NOT NULL;

ALTER TABLE forslag
    ALTER COLUMN deltaker_id SET NOT NULL;

ALTER TABLE tiltakskoordinator_deltakerliste_tilgang
    ALTER COLUMN created_at  SET NOT NULL,
    ALTER COLUMN modified_at SET NOT NULL;

ALTER TABLE vurdering
    ALTER COLUMN created_at  SET NOT NULL,
    ALTER COLUMN modified_at SET NOT NULL;


-- -------------------------------------------------------------------------
-- 4. UNIQUE-constraint (Funn 6)
--
-- NB: Funn 5 (FK på arrangor.overordnet_arrangor_id) ble droppet pga. risiko
-- for runtime-feil i ArrangorConsumer hvis Kafka leverer barn-arrangør før
-- parent. Indeksen i Funn 14 beholdes uavhengig av FK-en.
-- -------------------------------------------------------------------------


-- Funn 6: UNIQUE på personident (naturlig nøkkel)
ALTER TABLE nav_bruker
    ADD CONSTRAINT nav_bruker_personident_key UNIQUE (personident);


-- -------------------------------------------------------------------------
-- 5. CHECK-constraint (Funn 15)
-- -------------------------------------------------------------------------

ALTER TABLE tiltakskoordinator_deltakerliste_tilgang
    ADD CONSTRAINT tiltakskoordinator_deltakerliste_tilgang_gyldig_periode_check
    CHECK (gyldig_til IS NULL OR gyldig_til >= gyldig_fra);


-- -------------------------------------------------------------------------
-- 6. Nye indekser (Funn 8, 9, 10)
-- -------------------------------------------------------------------------

-- Funn 8 + 9: dekke FK-er på nav_bruker
CREATE INDEX IF NOT EXISTS nav_bruker_nav_enhet_id_idx
    ON nav_bruker (nav_enhet_id);

CREATE INDEX IF NOT EXISTS nav_bruker_nav_veileder_id_idx
    ON nav_bruker (nav_veileder_id);

-- Funn 10: dekke FK på koordinator-tilgang
CREATE INDEX IF NOT EXISTS tiltakskoordinator_deltakerliste_tilgang_deltakerliste_id_idx
    ON tiltakskoordinator_deltakerliste_tilgang (deltakerliste_id);



-- -------------------------------------------------------------------------
-- 7. Drop redundante indekser (Funn 11, 12, 13)
--
-- Disse dekkes nå av UNIQUE-indekser (enten eksisterende eller lagt til over).
-- -------------------------------------------------------------------------

DROP INDEX IF EXISTS nav_ansatt_nav_ident_idx;       -- Funn 11 (UNIQUE: nav_ansatt_nav_ident_key)
DROP INDEX IF EXISTS nav_enhet_nav_enhet_nr_idx;     -- Funn 12 (UNIQUE: nav_enhet_nav_enhet_nummer_key)
DROP INDEX IF EXISTS nav_bruker_personident_idx;     -- Funn 13 (UNIQUE: nav_bruker_personident_key, lagt til over)

