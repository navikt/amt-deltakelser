# amt-deltaker — ER-diagram

```mermaid
erDiagram
    tiltakstype {
        uuid id PK
        varchar navn
        varchar type UK
        varchar tiltakskode
        jsonb innsatsgrupper
        jsonb innhold
        timestamptz created_at
        timestamptz modified_at
    }

    arrangor {
        uuid id PK
        varchar navn
        varchar organisasjonsnummer UK
        uuid overordnet_arrangor_id FK "self-ref → arrangor(id)"
        timestamptz created_at
        timestamptz modified_at
    }

    nav_ansatt {
        uuid id PK
        varchar nav_ident UK
        varchar navn
        varchar telefonnummer
        varchar epost
        uuid nav_enhet_id FK
        timestamptz created_at
        timestamptz modified_at
    }

    nav_enhet {
        uuid id PK
        varchar nav_enhet_nummer UK
        varchar navn
        timestamptz created_at
        timestamptz modified_at
    }

    nav_bruker {
        uuid person_id PK
        varchar personident UK
        varchar fornavn
        varchar mellomnavn
        varchar etternavn
        varchar telefonnummer
        varchar epost
        jsonb adresse
        varchar adressebeskyttelse
        boolean er_skjermet
        uuid nav_veileder_id FK
        uuid nav_enhet_id FK
        jsonb oppfolgingsperioder
        varchar innsatsgruppe
        timestamptz created_at
        timestamptz modified_at
    }

    deltakerliste {
        uuid id PK
        varchar navn
        varchar status
        uuid arrangor_id FK "nullable"
        uuid tiltakstype_id FK
        date start_dato
        date slutt_dato
        varchar oppstart
        varchar gjennomforingstype
        boolean apent_for_pamelding
        varchar oppmote_sted
        varchar pameldingstype
        varchar prisinformasjon
        int antall_plasser
        timestamptz modified_at
        timestamptz created_at
    }

    deltaker {
        uuid id PK
        uuid person_id FK
        uuid deltakerliste_id FK
        date startdato
        date sluttdato
        double_precision dager_per_uke
        double_precision deltakelsesprosent
        text bakgrunnsinformasjon
        jsonb innhold
        varchar kilde
        boolean er_manuelt_delt_med_arrangor
        timestamptz created_at
        timestamptz modified_at
    }

    deltaker_status {
        uuid id PK
        uuid deltaker_id FK "NOT NULL"
        varchar type
        jsonb aarsak
        timestamptz gyldig_fra
        timestamptz gyldig_til
        timestamptz created_at
        timestamptz modified_at
    }

    vedtak {
        uuid id PK
        uuid deltaker_id FK "NOT NULL"
        timestamptz fattet
        timestamptz gyldig_til
        jsonb deltaker_ved_vedtak
        boolean fattet_av_nav
        uuid opprettet_av FK "→ nav_ansatt"
        uuid opprettet_av_enhet FK "→ nav_enhet"
        uuid sist_endret_av FK "→ nav_ansatt"
        uuid sist_endret_av_enhet FK "→ nav_enhet"
        timestamptz created_at
        timestamptz modified_at
    }

    deltaker_endring {
        uuid id PK
        uuid deltaker_id FK "NOT NULL"
        jsonb endring
        uuid endret_av FK "→ nav_ansatt"
        uuid endret_av_enhet FK "→ nav_enhet"
        uuid forslag_id FK "→ forslag"
        timestamptz behandlet
        timestamptz created_at
        timestamptz modified_at
    }

    forslag {
        uuid id PK
        uuid deltaker_id FK "NOT NULL"
        uuid arrangoransatt_id "ekstern, ingen FK"
        timestamptz opprettet
        varchar begrunnelse
        jsonb endring
        jsonb status
        timestamptz created_at
        timestamptz modified_at
    }

    endring_fra_arrangor {
        uuid id PK
        uuid deltaker_id FK "NOT NULL"
        uuid arrangor_ansatt_id "ekstern, ingen FK"
        timestamptz opprettet
        jsonb endring
        timestamptz created_at
        timestamptz modified_at
    }

    endring_fra_tiltakskoordinator {
        uuid id PK
        uuid deltaker_id FK "NOT NULL"
        uuid nav_ansatt_id FK "→ nav_ansatt"
        uuid nav_enhet_id FK "→ nav_enhet"
        timestamptz endret
        jsonb endring
        timestamptz created_at
        timestamptz modified_at
    }

    vurdering {
        uuid id PK
        uuid deltaker_id FK
        uuid opprettet_av_arrangor_ansatt_id "ekstern, ingen FK"
        varchar vurderingstype
        varchar begrunnelse
        timestamptz gyldig_fra
        timestamptz created_at
    }

    innsok_paa_felles_oppstart {
        uuid id PK
        uuid deltaker_id FK,UK
        timestamptz innsokt
        uuid innsokt_av FK "→ nav_ansatt"
        uuid innsokt_av_enhet FK "→ nav_enhet"
        jsonb deltakelsesinnhold_ved_innsok
        timestamptz utkast_delt
        boolean utkast_godkjent_av_nav
        timestamptz created_at
        timestamptz modified_at
    }

    importert_fra_arena {
        uuid deltaker_id PK,FK
        jsonb deltaker_ved_import
        timestamptz importert_dato
    }

    outbox_record {
        serial id PK
        varchar key
        jsonb value
        varchar value_type
        varchar topic
        timestamptz created_at
        timestamptz modified_at
        timestamptz processed_at
        varchar status
        int retry_count
        timestamptz retried_at
        text error_message
    }

    %% Relasjoner

    arrangor ||--o{ arrangor : "overordnet_arrangor_id"
    nav_ansatt }o--|| nav_enhet : "nav_enhet_id"
    nav_bruker }o--o| nav_ansatt : "nav_veileder_id"
    nav_bruker }o--o| nav_enhet : "nav_enhet_id"

    tiltakstype ||--o{ deltakerliste : "tiltakstype_id"
    arrangor ||--o{ deltakerliste : "arrangor_id"

    nav_bruker ||--o{ deltaker : "person_id"
    deltakerliste ||--o{ deltaker : "deltakerliste_id"

    deltaker ||--o{ deltaker_status : "deltaker_id"
    deltaker ||--o{ vedtak : "deltaker_id"
    deltaker ||--o{ deltaker_endring : "deltaker_id"
    deltaker ||--o{ forslag : "deltaker_id"
    deltaker ||--o{ endring_fra_arrangor : "deltaker_id"
    deltaker ||--o{ endring_fra_tiltakskoordinator : "deltaker_id"
    deltaker ||--o{ vurdering : "deltaker_id"
    deltaker ||--o| innsok_paa_felles_oppstart : "deltaker_id"
    deltaker ||--o| importert_fra_arena : "deltaker_id"

    nav_ansatt ||--o{ vedtak : "opprettet_av / sist_endret_av"
    nav_enhet ||--o{ vedtak : "opprettet_av_enhet / sist_endret_av_enhet"
    nav_ansatt ||--o{ deltaker_endring : "endret_av"
    nav_enhet ||--o{ deltaker_endring : "endret_av_enhet"
    nav_ansatt ||--o{ endring_fra_tiltakskoordinator : "nav_ansatt_id"
    nav_enhet ||--o{ endring_fra_tiltakskoordinator : "nav_enhet_id"
    nav_ansatt ||--o{ innsok_paa_felles_oppstart : "innsokt_av"
    nav_enhet ||--o{ innsok_paa_felles_oppstart : "innsokt_av_enhet"
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
| `outbox_record` | Kafka outbox for event-publisering | — |

## Merknader

- **Ekstern-ID-er uten FK:** `arrangor_ansatt_id` (endring_fra_arrangor, forslag) og `opprettet_av_arrangor_ansatt_id` (vurdering) peker på `amt-arrangor`-databasen — bevisst uten FK constraint.
- **Aktiv status:** Mønsteret `gyldig_til IS NULL AND gyldig_fra <= CURRENT_TIMESTAMP` brukes for å finne aktiv deltaker-status.
- **Planlagte statusendringer:** En deltaker kan ha maks 2 aktive rader i `deltaker_status`: én nåværende + én fremtidig slutt-status.

