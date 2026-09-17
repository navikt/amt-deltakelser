-- V31 — Database review cleanup (amt-distribusjon)
-- Se funn-amt-distribusjon-db.md for full bakgrunn og kode-verifisering.
--
-- Innhold:
--   1. Funn 2 — backfill + NOT NULL DEFAULT FALSE på journalforingstatus.kan_ikke_*
--   2. Funn 3 — NOT NULL på varsel.er_eksternt_varsel (ingen backfill, 0 NULL i pre-flight)
--   3. Funn 5 — FK journalforingstatus.hendelse_id -> hendelse(id)
--   4. Funn 6 — Self-FK varsel.revarsel_for_varsel -> varsel(id)
--
-- Alt kjøres i én Flyway-transaksjon. Ingen CREATE INDEX CONCURRENTLY,
-- ingen REINDEX. Forventet kjøretid: ~10-30 sekunder dominert av
-- backfill (~454k rader) og SET NOT NULL-validering på de største tabellene.

-- =============================================================================
-- Funn 2 — journalforingstatus.kan_ikke_distribueres / kan_ikke_journalfores
-- =============================================================================
-- Pre-flight (per 2026-05-02): 454 821 NULL kan_ikke_distribueres,
--                              12 981 NULL kan_ikke_journalfores.
-- Kotlin-domeneslogikken bruker `== true`, så NULL og FALSE er allerede
-- semantisk likeverdige. Backfill endrer ikke oppførsel.

UPDATE journalforingstatus
SET kan_ikke_distribueres = COALESCE(kan_ikke_distribueres, FALSE),
    kan_ikke_journalfores = COALESCE(kan_ikke_journalfores, FALSE)
WHERE kan_ikke_distribueres IS NULL
   OR kan_ikke_journalfores IS NULL;

ALTER TABLE journalforingstatus
    ALTER COLUMN kan_ikke_distribueres SET DEFAULT FALSE,
    ALTER COLUMN kan_ikke_distribueres SET NOT NULL,
    ALTER COLUMN kan_ikke_journalfores SET DEFAULT FALSE,
    ALTER COLUMN kan_ikke_journalfores SET NOT NULL;

-- =============================================================================
-- Funn 3 — varsel.er_eksternt_varsel
-- =============================================================================
-- Pre-flight (per 2026-05-02): 0 NULL-rader. DEFAULT FALSE er allerede satt
-- (V12), så ingen backfill nødvendig.

ALTER TABLE varsel
    ALTER COLUMN er_eksternt_varsel SET NOT NULL;

-- =============================================================================
-- Funn 5 — FK journalforingstatus.hendelse_id -> hendelse(id)
-- =============================================================================
-- Pre-flight (per 2026-05-02): 0 orphans.
-- Kode-verifisert: HendelseConsumer.consume committer hendelse i en
-- transaksjon før journalforingService.handleHendelse(...) kjører — så
-- journalforingstatus opprettes alltid etter at hendelse er persistert.

ALTER TABLE journalforingstatus
    ADD CONSTRAINT journalforingstatus_hendelse_id_fkey
        FOREIGN KEY (hendelse_id) REFERENCES hendelse (id);

-- =============================================================================
-- Funn 6 — Self-FK varsel.revarsel_for_varsel -> varsel(id)
-- =============================================================================
-- Pre-flight (per 2026-05-02): 0 orphans.
-- Kode-verifisert: Varsel.Companion.revarsel(varsel) er eneste vei til
-- non-null revarsel_for_varsel, og krever et eksisterende (lest fra DB)
-- foreldre-varsel. Varsler slettes ikke, så ON DELETE NO ACTION er trygt.

ALTER TABLE varsel
    ADD CONSTRAINT varsel_revarsel_for_varsel_fkey
        FOREIGN KEY (revarsel_for_varsel) REFERENCES varsel (id);

