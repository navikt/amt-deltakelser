# Funn fra databasegjennomgang — amt-deltaker

Basert på output fra spørringer mot information_schema (kolonner, constraints) og pg_indexes (indekser) den 26. april 2026.

> **Status:** Kolonner, constraints, indekser, tabellstørrelser og repository-spørringer er gjennomgått.
> Flyway-migrering `V66__database_opprydning.sql` dekker funn 1, 3, 5, 17, 18, 19, 20, 21, 24.
> Flyway-migrering `V67__manglende_fk_constraints.sql` dekker funn 25.

## Sammendrag

| Alvorlighet | Antall funn | Fikset i V66 | Fikset i V67 | VENT |
|---|---|---|---|---|
| 🔴 Høy | 4 | 3 (funn 1, 3, 5) | 0 | 1 (funn 4) |
| 🟡 Middels | 4 | 1 (funn 21) | 1 (funn 25) | 1 (funn 11) |
| 🟢 Lav | 5 | 5 (funn 17–20, 24) | 0 | 0 |

---

## 🔴 Høy alvorlighet

### 1. ✅ V66 — timestamp without time zone brukt inkonsistent

Alle tabeller bruker `timestamp WITH time zone` (timestamptz), unntatt:

| Tabell | Kolonne | Type |
|---|---|---|
| `innsok_paa_felles_oppstart` | `innsokt` | `timestamp without time zone` |
| `innsok_paa_felles_oppstart` | `utkast_delt` | `timestamp without time zone` |

**Konsekvens:** Tolkning av tidsstempler mot Norge (UTC+1/+2) gir 1–2 timers feil avhengig av tidssone på prosess som leser/skriver. Spesielt kritisk siden `innsokt` brukes i `Deltaker.soktInnDato()`.

**Fix:**

```sql
ALTER TABLE innsok_paa_felles_oppstart
    ALTER COLUMN innsokt TYPE timestamptz USING innsokt AT TIME ZONE 'Europe/Oslo',
    ALTER COLUMN utkast_delt TYPE timestamptz USING utkast_delt AT TIME ZONE 'Europe/Oslo';
```

> **Verifisert fra kode og Dockerfile:** `InnsokPaaFellesOppstartService` bruker `LocalDateTime.now()`. Dockerfile setter `ENV TZ="Europe/Oslo"`, så JVM kjører i norsk tidssone. Verdiene i databasen er **norsk lokal tid** (CET/CEST).

---


### 3. ✅ V66 — nav_bruker.personident mangler UNIQUE

`personident` er fnr/dnr — global unik identifikator. Burde vært UNIQUE.

Det finnes allerede en non-unique indeks `nav_bruker_personident_idx` — så oppslag er raske, men den hindrer ikke duplikater.

**Sjekk for duplikater:**

```sql
SELECT personident, COUNT(*)
FROM nav_bruker
GROUP BY personident
HAVING COUNT(*) > 1;
```
A: Ingen duplikater

**Fix (etter eventuell opprydning):**

```sql
-- Drop den eksisterende non-unique indeksen (UNIQUE-indeksen erstatter den)
DROP INDEX nav_bruker_personident_idx;

ALTER TABLE nav_bruker
    ADD CONSTRAINT nav_bruker_personident_key UNIQUE (personident);
```

---

### 4. ⏸️ VENT — deltaker_status — partial unique må ta hensyn til fremtidige statuser
Vent med denne tabellen.

Kode-mønsteret `WHERE gyldig_til IS NULL` brukes for å finne aktiv status. Uten constraint kan to samtidige skrivere lage to "aktive" statuser, og leser-kode får tilfeldig hvilken den treffer.

**Eksisterende indekser:** Det finnes flere smarte partial indexes på `deltaker_status`:

- `deltaker_status_join_deltaker_idx2` — covering index for join (`deltaker_id, gyldig_fra`) `WHERE gyldig_til IS NULL`
- `deltaker_status_deltar_aktiv_idx` — `(deltaker_id) WHERE gyldig_til IS NULL AND type = 'DELTAR'`
- `deltaker_status_slutt_aktiv_idx` — `(deltaker_id) WHERE gyldig_til IS NULL AND type IN ('AVBRUTT', 'FULLFORT', 'HAR_SLUTTET')`

Men **ingen av dem er UNIQUE** — de optimaliserer oppslag, men forhindrer ikke duplikater.

**Duplikat-sjekk viser:** Det finnes deltakere med 2 aktive rader (`gyldig_til IS NULL`). Mønsteret er alltid **DELTAR (nå) + HAR_SLUTTET/AVBRUTT (fremtidig `gyldig_fra`)**. Dette er et "planlagt statusendring"-pattern der slutt-statusen er forhåndsopprettet.

En enkel `UNIQUE (deltaker_id) WHERE gyldig_til IS NULL` er **ikke mulig**.

**Alternativ fix — oppgrader eksisterende indekser til UNIQUE:**

De eksisterende non-unique partial indexes kan erstattes med UNIQUE-varianter (bekreftet: ingen deltaker har 3+ aktive rader):

```sql
-- Erstatt eksisterende non-unique med UNIQUE
DROP INDEX deltaker_status_deltar_aktiv_idx;
CREATE UNIQUE INDEX deltaker_status_deltar_aktiv_idx
    ON deltaker_status (deltaker_id)
    WHERE gyldig_til IS NULL AND type IN ('DELTAR', 'VENTER_PA_OPPSTART');

DROP INDEX deltaker_status_slutt_aktiv_idx;
CREATE UNIQUE INDEX deltaker_status_slutt_aktiv_idx
    ON deltaker_status (deltaker_id)
    WHERE gyldig_til IS NULL AND type IN ('HAR_SLUTTET', 'AVBRUTT', 'FULLFORT');
```

> ✅ Verifisert: 0 deltakere med 3+ aktive rader. Mønsteret er alltid maks 2: én nåværende (DELTAR/VENTER_PA_OPPSTART) + én fremtidig slutt-status.

---

### 5. ✅ V66 — Nullable FK-er som burde være NOT NULL

Alle FK-er har `delete_rule = NO ACTION`, så ingen `ON DELETE SET NULL` kan sette dem til NULL automatisk. De er nullable kun av historiske grunner.

| Tabell | Kolonne | Burde være |
|---|---|---|
| `deltaker_endring` | `deltaker_id` | NOT NULL |
| `deltaker_status` | `deltaker_id` | NOT NULL |
| `endring_fra_arrangor` | `deltaker_id` | NOT NULL |
| `endring_fra_tiltakskoordinator` | `deltaker_id` | NOT NULL |
| `forslag` | `deltaker_id` | NOT NULL |
| `vedtak` | `deltaker_id` | NOT NULL |

**Fix-mønster (per tabell, etter cleanup):**

A: Alle tabeller er klare for NOT NULL.

```sql
-- 1. Finn rader med NULL
SELECT COUNT(*) FROM deltaker_endring WHERE deltaker_id IS NULL;

-- 2. Slett eller fiks dem

-- 3. Sett NOT NULL
ALTER TABLE deltaker_endring
    ALTER COLUMN deltaker_id SET NOT NULL;
```

---

## 🟡 Middels alvorlighet

### 11. ⏸️ VENT — dager_per_uke og deltakelsesprosent som double precision
Ikke gjør noe med dette punktet nå

For forretningsdata anbefales `numeric` for å unngå floating-point-overraskelser (`0.1 + 0.2 != 0.3`).

```sql
ALTER TABLE deltaker
    ALTER COLUMN dager_per_uke      TYPE numeric(3,1),
    ALTER COLUMN deltakelsesprosent TYPE numeric(5,2);
```

> Sjekk at eksisterende verdier ikke overskrider grensene først.

---

## 🟢 Lav alvorlighet

### 17. ✅ V66 — Dupliserte indekser — nav_ansatt.nav_ident

Både UNIQUE-constraint og separat indeks:

```
nav_ansatt_nav_ident_key   UNIQUE INDEX  btree (nav_ident)
nav_ansatt_nav_ident_idx   INDEX         btree (nav_ident)
```

UNIQUE-indeksen dekker allerede alle oppslag som non-unique-indeksen ville gjort. Drop duplikaten:

```sql
DROP INDEX nav_ansatt_nav_ident_idx;
```

### 18. ✅ V66 — Dupliserte indekser — nav_enhet.nav_enhet_nummer

Samme mønster som ovenfor:

```
nav_enhet_nav_enhet_nummer_key   UNIQUE INDEX  btree (nav_enhet_nummer)
nav_enhet_nav_enhet_nr_idx       INDEX         btree (nav_enhet_nummer)
```

```sql
DROP INDEX nav_enhet_nav_enhet_nr_idx;
```

### 19. ✅ V66 — Typo i indeksnavn

```
endring_fra_tiltaksoordinator_deltaker_id_idx
```

Mangler en `k` — skal være `tiltakskoordinator`. Funksjonelt uproblematisk, men forvirrende ved feilsøking.

```sql
ALTER INDEX endring_fra_tiltaksoordinator_deltaker_id_idx
    RENAME TO endring_fra_tiltakskoordinator_deltaker_id_idx;
```

### 20. ✅ V66 — Manglende FK-indeks — deltakerliste.tiltakstype_id

`deltakerliste` har FK til `tiltakstype(id)`, men ingen indeks på `tiltakstype_id`. Uten indeks går `JOIN deltakerliste ... JOIN tiltakstype` via sequential scan på FK-kolonnen.

`deltakerliste.arrangor_id` har indeks ✓, men `tiltakstype_id` mangler.

```sql
CREATE INDEX deltakerliste_tiltakstype_id_idx
    ON deltakerliste USING btree (tiltakstype_id);
```

> For en liten tabell er dette kanskje ikke performance-kritisk, men det er god praksis.

---


#### 🟡 deltaker_status: indekser > tabelldata

327 MB i indekser vs 277 MB tabelldata. Med 5+ partial indexes på denne tabellen er dette forventet, men verdt å vurdere om alle trengs (se funn 17–18 om duplikat-indekser).

#### 🟡 importert_fra_arena er største tabell (765 MB)

1,5 mill rader — nesten like mange som `deltaker` (1,66 mill). Nærmest en 1:1-kopi av importert deltaker-snapshot som JSON. Vurder om historiske rader kan arkiveres eller om tabellen kan ryddes etter import.

#### ℹ️ Migrasjonsprioritering

Tabellene som trenger datakorrekthet-fix (funn 1–5) rangert etter størrelse:

1. `nav_bruker` — 714 MB / 786k rader (UNIQUE-constraint, funn 3)
2. `deltaker_status` — 604 MB / 2,3M rader (partial unique, funn 4)
3. `vedtak` — 219 MB / 143k rader (vedtak-modell, funn 2)
4. `innsok_paa_felles_oppstart` — 5,8 MB / 18k rader (timestamptz, funn 1 — liten tabell, rask migrering)

---

## Prioritert tiltaksliste

### Først (datakorrekthet)

1. Migrer `innsok_paa_felles_oppstart.innsokt` og `utkast_delt` til `timestamptz`
2. VENT Legg på partial unique for `deltaker_status` aktiv status
3. UNIQUE på `nav_bruker.personident`

### Deretter (datakvalitet)

5. NOT NULL på FK-er i `deltaker_endring`, `deltaker_status`, `endring_fra_arrangor`, `endring_fra_tiltakskoordinator`, `forslag`, `vedtak`

### Til slutt (rydde og konsistens)

6. VENT `numeric` i stedet for `double precision` for prosent og dager
7. VENT Legg til `modified_at` på `vurdering`

---

## Gjennomgang av repository-spørringer

Gjennomgått 16 repositories i `amt-deltaker/src/main/kotlin` mot databasefunn.

### DeltakerRepository

**`buildDeltakerSql`** (brukes av alle lesespørringer):
```sql
JOIN deltaker_status ds ON d.id = ds.deltaker_id AND ds.gyldig_til IS NULL AND ds.gyldig_fra <= CURRENT_TIMESTAMP
LEFT JOIN vedtak v ON d.id = v.deltaker_id AND v.gyldig_til IS NULL
```

| Observasjon | Referanse |
|---|---|
| ✅ `ds.gyldig_fra <= CURRENT_TIMESTAMP` — filtrerer bort fremtidige statuser, konsistent med funn 4 |  |
| ✅ Vedtak-join filtrerer `gyldig_til IS NULL` — men UNIQUE-constraint (funn 2) gjør at det uansett maks er 1 rad | Funn 2 |
| ⚠️ `getDeltakereForAvsluttetDeltakerliste` bruker `LIMIT 5_000` — potensielt trunkerer resultater uten varsling | |
| ⚠️ `getDeltakereMedStatus` joiner `deltaker_status` uten å filtrere `gyldig_fra` — inkluderer fremtidige statuser | Inkonsistent med `buildDeltakerSql` |
| ✅ `upsert` bruker `ON CONFLICT (id)` — PK-basert, uproblematisk | |

### DeltakerStatusRepository

| Observasjon | Referanse |
|---|---|
| ✅ `lagreStatus` bruker `ON CONFLICT (id) DO NOTHING` — trygt mot duplikater | |
| ✅ `deaktiverTidligereStatuser` bruker riktig logikk for fremtidige statuser (enten sluttdato endret, eller `gyldig_fra < CURRENT_TIMESTAMP`, eller ikke-avsluttende type) | Funn 4 |
| ✅ `slettTidligereFremtidigeStatuser` — rydder fremtidige statuser korrekt | |
| ⚠️ `getAvsluttendeDeltakerStatuserForOppdatering` bruker `gyldig_fra < current_date + interval '1 day'` — tidssone-avhengig, men tabellen bruker `timestamptz` så OK | |

### VedtakRepository

| Observasjon | Referanse |
|---|---|
| ✅ `upsert` bruker `ON CONFLICT (id)` — UNIQUE på `deltaker_id` forhindrer duplikater for samme deltaker | Funn 2 |
| ✅ `getForDeltaker` henter uten `WHERE gyldig_til IS NULL` — returnerer også kansellerte vedtak. Med UNIQUE kan maks én rad eksistere, så dette gir enten aktivt eller kansellert vedtak | Funn 2 |
| ℹ️ Partial index `vedtak_aktiv_idx` er redundant gitt absolutt UNIQUE — kan droppes | Funn 2 |

### InnsokPaaFellesOppstartRepository

| Observasjon | Referanse |
|---|---|
| 🔴 `rowMapper` leser `innsokt` som `localDateTime` og `utkast_delt` som `localDateTimeOrNull` — men disse lagres som `timestamp without time zone`. Kotlin-kode tolker dem som lokal tid, men databasen lagrer uten sone | Funn 1 |
| ⚠️ `insert` — ren INSERT uten ON CONFLICT. Duplikat-insert feiler med PK-violation (id) | |
| ⚠️ `getForDeltaker` bruker `.asSingle` — returnerer `null` ved 0 rader, men vil kaste exception ved 2+ rader. Tabellen har ingen UNIQUE på `deltaker_id` | Nytt funn |

### ImportertFraArenaRepository

| Observasjon | Referanse |
|---|---|
| ✅ `upsert` bruker `ON CONFLICT (deltaker_id)` — PK-basert, korrekt for 1:1-tabell | |
| ✅ `rowMapper` leser `importert_dato` som `localDateTime` — OK, dette er `timestamptz` i databasen | |

### NavBrukerRepository

| Observasjon | Referanse |
|---|---|
| ✅ `upsert` bruker `ON CONFLICT (person_id)` — PK-basert | |
| ⚠️ `personident` kan oppdateres via upsert (fnr-bytte). Uten UNIQUE-constraint kan to rader ha samme `personident` etter race condition | Funn 3 |
| ✅ `get(personident)` bruker indeks `nav_bruker_personident_idx` | Funn 3 |

### DeltakerEndringRepository

| Observasjon | Referanse |
|---|---|
| ⚠️ `selectDeltakerEndring` joiner `deltaker_status` med `gyldig_til IS NULL AND gyldig_fra <= CURRENT_TIMESTAMP AND type != 'FEILREGISTRERT'` — skjuler endringer for feilregistrerte deltakere. Bevisst? | |
| ⚠️ `getUbehandletDeltakelsesmengder` — JSON-filtrering `de.endring->>'type'` og `de.endring->>'gyldigFra'` — ingen indeks på JSON-felt. Ved mange rader kan dette bli tregt | |

### ForslagRepository

| Observasjon | Referanse |
|---|---|
| ✅ `upsert` oppdaterer `deltaker_id` i ON CONFLICT — kan flytte forslag mellom deltakere. Bevisst? | |
| ✅ `kanLagres` sjekker bare at `deltaker` eksisterer, ikke status | |

### EndringFraArrangorRepository

| Observasjon | Referanse |
|---|---|
| ✅ `insert` bruker `ON CONFLICT (id) DO NOTHING` — idempotent | |
| ℹ️ Ingen modifikasjon/oppdatering — append-only pattern | |

### EndringFraTiltakskoordinatorRepository

| Observasjon | Referanse |
|---|---|
| ✅ `insert` — ren INSERT uten ON CONFLICT, batch-operasjon | |
| ⚠️ Ingen ON CONFLICT — duplikat-insert feiler med PK-violation | |

### VurderingRepository

| Observasjon | Referanse |
|---|---|
| ✅ `upsert` bruker `ON CONFLICT (id)`, men oppdaterer *ikke* `modified_at` — tabellen har heller ikke denne kolonnen | Funn 8 |
| ⚠️ `getForDeltaker` returnerer alle vurderinger uten sortering — kaller-koden må vite at "nyeste gyldig_fra" er gjeldende | Funn 9 |

### DeltakerlisteRepository

| Observasjon | Referanse |
|---|---|
| ✅ `upsert(Deltakerliste)` setter `arrangor_id` og `status` — begge nullable i DB | Funn 6, 7 |
| ⚠️ `upsert(GjennomforingInsertDbo)` setter **ikke** `arrangor_id` — vil bli NULL. Etterfølges antagelig av `update(EnkeltplassGjennomforingUpdateDbo)` som setter det. Støtter at `arrangor_id` bevisst er nullable (kladd-pattern) | Funn 6 |
| ✅ `get` bruker `LEFT JOIN arrangor` — håndterer nullable arrangor_id korrekt | |

### ArrangorRepository, NavAnsattRepository, NavEnhetRepository, TiltakstypeRepository

Ingen avvik. Alle bruker `ON CONFLICT (id) DO UPDATE` korrekt.

---

### Nye funn fra query-gjennomgang

#### 21. ✅ V66 — 🟡 InnsokPaaFellesOppstart mangler UNIQUE på deltaker_id

`getForDeltaker` bruker `.asSingle` — dette kaster exception ved 2+ rader. Ingen UNIQUE-constraint på `deltaker_id`.

**Duplikat-sjekk viser:** 7 deltakere med duplikater (maks 8 rader for én deltaker). Alle fra 2026-01-08 med sekunder mellom — retry-bug i insert som mangler idempotency-guard. Alle har `utkast_godkjent_av_nav = false`.

**Fix i V66:** Sletter duplikater (beholder nyeste per deltaker), deretter UNIQUE constraint.

#### 22. 🟡 getDeltakereMedStatus inkluderer fremtidige statuser

`DeltakerRepository.getDeltakereMedStatus` filtrerer `gyldig_fra < CURRENT_TIMESTAMP` men mangler `gyldig_fra <= CURRENT_TIMESTAMP` som brukes i `buildDeltakerSql`. Viktigere: den bruker en helt annen join (direkte `deltaker_status` uten `buildDeltakerSql`) og returnerer bare ID-er. Potensielt inkonsistent oppførsel.

#### 24. ✅ V66 — 🟢 JSON-felt mangler indeks

`DeltakerEndringRepository.getUbehandletDeltakelsesmengder` filtrerer på `de.endring->>'type'` og `de.endring->>'gyldigFra'`. Uten GIN- eller B-tree-indeks på disse JSON-feltene er denne spørringen en sequential scan på `deltaker_endring` (72 MB / 195k rader).

```sql
CREATE INDEX deltaker_endring_endring_type_idx
    ON deltaker_endring ((endring->>'type'))
    WHERE behandlet IS NULL;
```

---

#### 25. ✅ V67 — 🟡 Manglende FK constraints på interne uuid-kolonner

8 uuid-kolonner som peker på interne tabeller mangler FK constraint. Verifisert: 0 orphaned rader.

| Tabell | Kolonne | Peker på |
|---|---|---|
| `arrangor` | `overordnet_arrangor_id` | `arrangor(id)` (self-ref) |
| `endring_fra_tiltakskoordinator` | `nav_ansatt_id` | `nav_ansatt(id)` |
| `vedtak` | `opprettet_av` | `nav_ansatt(id)` |
| `vedtak` | `opprettet_av_enhet` | `nav_enhet(id)` |
| `vedtak` | `sist_endret_av` | `nav_ansatt(id)` |
| `vedtak` | `sist_endret_av_enhet` | `nav_enhet(id)` |
| `innsok_paa_felles_oppstart` | `innsokt_av` | `nav_ansatt(id)` |
| `innsok_paa_felles_oppstart` | `innsokt_av_enhet` | `nav_enhet(id)` |

**Bevisst uten FK** (peker på `amt-arrangor`, annen app/database):

| Tabell | Kolonne |
|---|---|
| `endring_fra_arrangor` | `arrangor_ansatt_id` |
| `forslag` | `arrangoransatt_id` |
| `vurdering` | `opprettet_av_arrangor_ansatt_id` |

