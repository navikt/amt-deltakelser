# amt-deltaker — ER-diagram

## Oversikt

Diagrammet viser kun primærnøkler, fremmednøkler og unike nøkler for lesbarhet.
Se [tabelloversikten](#tabelloversikt) for alle kolonner.

```mermaid
erDiagram
    %% ── Stamdata ──
    tiltakstype {
        uuid id PK
    }

    arrangor {
        uuid id PK
        uuid overordnet_arrangor_id FK "self-ref"
    }

    nav_ansatt {
        uuid id PK
        varchar nav_ident UK
        uuid nav_enhet_id FK
    }

    nav_enhet {
        uuid id PK
        varchar nav_enhet_nummer UK
    }

    nav_bruker {
        uuid person_id PK
        varchar personident UK
        uuid nav_veileder_id FK
        uuid nav_enhet_id FK
    }

    %% ── Kjerne ──
    deltakerliste {
        uuid id PK
        uuid arrangor_id FK "nullable"
        uuid tiltakstype_id FK
    }

    deltaker {
        uuid id PK
        uuid person_id FK
        uuid deltakerliste_id FK
    }

    deltaker_status {
        uuid id PK
        uuid deltaker_id FK
    }

    vedtak {
        uuid id PK
        uuid deltaker_id FK
        uuid opprettet_av FK
        uuid opprettet_av_enhet FK
        uuid sist_endret_av FK
        uuid sist_endret_av_enhet FK
    }

    %% ── Endringslogg ──
    deltaker_endring {
        uuid id PK
        uuid deltaker_id FK
        uuid endret_av FK
        uuid endret_av_enhet FK
        uuid forslag_id FK
    }

    forslag {
        uuid id PK
        uuid deltaker_id FK
    }

    endring_fra_arrangor {
        uuid id PK
        uuid deltaker_id FK
    }

    endring_fra_tiltakskoordinator {
        uuid id PK
        uuid deltaker_id FK
        uuid nav_ansatt_id FK
        uuid nav_enhet_id FK
    }

    vurdering {
        uuid id PK
        uuid deltaker_id FK
    }

    %% ── Tilleggstabeller ──
    innsok_paa_felles_oppstart {
        uuid id PK
        uuid deltaker_id FK,UK
        uuid innsokt_av FK
        uuid innsokt_av_enhet FK
    }

    importert_fra_arena {
        uuid deltaker_id PK,FK
    }

    %% ── Relasjoner: Stamdata ──
    arrangor ||--o{ arrangor : "overordnet"
    nav_ansatt }o--|| nav_enhet : ""

    %% ── Relasjoner: Kjerne ──
    tiltakstype ||--o{ deltakerliste : ""
    arrangor ||--o{ deltakerliste : ""
    nav_bruker ||--o{ deltaker : ""
    deltakerliste ||--o{ deltaker : ""

    %% ── Relasjoner: Deltaker → barn ──
    deltaker ||--o{ deltaker_status : ""
    deltaker ||--o{ vedtak : ""
    deltaker ||--o{ deltaker_endring : ""
    deltaker ||--o{ forslag : ""
    deltaker ||--o{ endring_fra_arrangor : ""
    deltaker ||--o{ endring_fra_tiltakskoordinator : ""
    deltaker ||--o{ vurdering : ""
    deltaker ||--o| innsok_paa_felles_oppstart : ""
    deltaker ||--o| importert_fra_arena : ""

    %% ── Relasjoner: Nav-ansatt/enhet ──
    nav_ansatt ||--o{ vedtak : "opprettet/endret av"
    nav_enhet ||--o{ vedtak : "opprettet/endret av enhet"
    nav_ansatt ||--o{ deltaker_endring : ""
    nav_enhet ||--o{ deltaker_endring : ""
    nav_ansatt ||--o{ endring_fra_tiltakskoordinator : ""
    nav_ansatt ||--o{ innsok_paa_felles_oppstart : ""
    nav_enhet ||--o{ innsok_paa_felles_oppstart : ""
    forslag ||--o{ deltaker_endring : "forslag_id"
```

## Tabelloversikt

| Tabell | Beskrivelse | Størrelse (ca.) |
|---|---|---|
| `deltaker` | Hovedtabell for deltakere | 1.66M rader |
| `deltaker_status` | Statushistorikk per deltaker (gyldig_til IS NULL = aktiv) | 2.3M rader |
| `nav_bruker` | Persondata for brukere | 786k rader |
| `deltakerliste` | Tiltaksgjennomføringer (grupper eller enkeltplass) | ~30k rader |
| `vedtak` | Nav-vedtak knyttet til deltaker | 143k rader |
| `deltaker_endring` | Endringslogg fra Nav-veiledere | 195k rader |
| `forslag` | Forslag fra arrangør-ansatte | — |
| `endring_fra_arrangor` | Endringer initiert av arrangør | — |
| `endring_fra_tiltakskoordinator` | Endringer fra tiltakskoordinator | — |
| `vurdering` | Arrangørs vurdering av deltaker | — |
| `innsok_paa_felles_oppstart` | Innsøking til felles oppstart | 18k rader |
| `importert_fra_arena` | Arena-importerte deltaker-snapshots | 1.5M rader (765 MB) |
| `tiltakstype` | Tiltakstype-definisjon | Liten |
| `arrangor` | Arrangører (underordnet/overordnet) | Liten |
| `nav_ansatt` | Nav-ansatte | Liten |
| `nav_enhet` | Nav-enheter | Liten |
| `outbox_record` | Kafka outbox for event-publisering (frittstående) | — |

## Merknader

- **Ekstern-ID-er uten FK:** `arrangor_ansatt_id` (endring_fra_arrangor, forslag) og `opprettet_av_arrangor_ansatt_id` (vurdering) peker på `amt-arrangor`-databasen — bevisst uten FK constraint.
- **Aktiv status:** Mønsteret `gyldig_til IS NULL AND gyldig_fra <= CURRENT_TIMESTAMP` brukes for å finne aktiv deltaker-status.
- **Planlagte statusendringer:** En deltaker kan ha maks 2 aktive rader i `deltaker_status`: én nåværende + én fremtidig slutt-status.
- **outbox_record** er en frittstående tabell uten relasjoner til resten av skjemaet.
