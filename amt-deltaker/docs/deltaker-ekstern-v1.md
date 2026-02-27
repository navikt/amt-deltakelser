# Dokumentasjon for topicet `amt.deltaker-ekstern-v1`

## Innhold

1. [Beskrivelse](#beskrivelse)
1. [Meldinger](#meldinger)
    1. [Key](#key)
    1. [Deltaker](#deltaker)
    1. [DeltakerStatus](#status)
    1. [Aarsak](#aarsak)
    1. [Skjema](#skjema)

## Beskrivelse

På topicen `amt.deltaker-ekstern-v1` publiseres det siste øyeblikksbildet av deltakere på følgende tiltakstyper:

- ARBEIDSFORBEREDENDE_TRENING 
- ARBEIDSRETTET_REHABILITERING 
- AVKLARING 
- OPPFOLGING 
- VARIG_TILRETTELAGT_ARBEID_SKJERMET 
- DIGITALT_OPPFOLGINGSTILTAK 
- GRUPPE_ARBEIDSMARKEDSOPPLAERING 
- GRUPPE_FAG_OG_YRKESOPPLAERING 
- JOBBKLUBB 
- ENKELTPLASS_ARBEIDSMARKEDSOPPLAERING 
- ENKELTPLASS_FAG_OG_YRKESOPPLAERING 
- HOYERE_UTDANNING 
- ARBEIDSMARKEDSOPPLAERING 
- NORSKOPPLAERING_GRUNNLEGGENDE_FERDIGHETER_FOV 
- STUDIESPESIALISERING 
- FAG_OG_YRKESOPPLAERING 
- HOYERE_YRKESFAGLIG_UTDANNING

Topicen inneholder deltakere som kan ha **adressebeskyttelse** (kode 6/7), og skjermede personer (egen ansatt).

Deltakere kan bli slettet, da vil det bli produsert en tombstone for den deltakeren. En tombstone er en melding med `null` som verdi, hvor key er deltakerens id.

Topicen er satt opp med evig retention og compaction, så den skal inneholde alle deltakere som har vært registrert på de
nevnte tiltakene. Compaction betyr at kun siste melding for hver key beholdes. Konsumenter som leser fra starten av topicen vil få siste versjon av hver deltaker.

## Meldinger

**Eksempel payload:**

```json
{
  "id": "bd3b6087-2029-481b-bcf0-e37354c00286",
  "gjennomforingId": "1487f7fe-156c-41d7-8d90-bf108dd1b4d2",
  "personIdent": "12345678942",
  "startDato": "2022-02-25",
  "sluttDato": "2022-05-20",
  "status": {
    "type": "HAR_SLUTTET",
    "tekst": "Har sluttet",
    "aarsak": {
      "type": "FATT_JOBB",
      "beskrivelse": "Fått jobb"
    },
    "opprettetTidspunkt": "2023-10-24T11:47:48.254204"
  },
  "registrertTidspunkt": "2022-01-27T16:13:39",
  "endretTidspunkt": "2023-10-24T11:47:48.254204",
  "kilde": "KOMET",
  "innhold": {
    "ledetekst": "Arbeidsforberedende trening er et tilbud for deg som først ønsker å jobbe i et tilrettelagt arbeidsmiljø. Du får veiledning og støtte av en veileder. Sammen kartlegger dere hvordan din kompetanse, interesser og ferdigheter påvirker muligheten din til å jobbe.",
    "valgtInnhold": [
      {
        "tekst": "Karriereveiledning",
        "innholdskode": "karriereveiledning"
      },
      {
        "tekst": "Kartlegge hvordan helsen din påvirker muligheten din til å jobbe",
        "innholdskode": "kartlegge-helse"
      }
    ]
  },
  "deltakelsesmengder": [
    {
      "deltakelsesprosent": 50,
      "dagerPerUke": 3,
      "gyldigFraDato": "2022-02-25",
      "opprettetTidspunkt": "2022-01-27T00:00:00"
    }
  ]
}
```

### Key

- Format: `uuid`
- Beskrivelse: En unik id som identifiserer en enkelt deltaker / deltakelse på ett tiltak.

### Deltaker

**Format-notater:**
- `date` - Dato i ISO-8601 format (YYYY-MM-DD)
- `datetime` - Tidsstempel i ISO-8601 format (YYYY-MM-DDTHH:MM:SS eller YYYY-MM-DDTHH:MM:SS.ssssss)

| Felt                    | Format         | Beskrivelse                                                                                                                                                                                                                                    |
|-------------------------|----------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **id**                  | `uuid`         | En unik id som identifiserer en enkelt deltaker / deltakelse på ett tiltak. Samme som `Key`                                                                                                                                                    |
| **gjennomforingId**     | `uuid`         | En unik id som identifiserer en tiltaksgjennomføring fra [Team Valp](https://github.com/navikt/mulighetsrommet)                                                                                                                                |
| **personIdent**         | `string`       | Gjeldende folkeregisterident for personen, hvis en folkeregisterident ikke finnes kan det være en av: npid eller aktør-id                                                                                                                      |
| **startDato**           | `date\|null`   | Dagen deltakeren starter/startet på tiltaket                                                                                                                                                                                                   | 
| **sluttDato**           | `date\|null`   | Dagen deltakeren slutter/sluttet på tiltaket                                                                                                                                                                                                   |
| **status**              | `object`       | Nåværende status på deltakeren, forteller f.eks om deltakeren deltar på tiltaket akkurat nå eller venter på oppstart osv. Se [Status](#status)                                                                                                 |
| **registrertTidspunkt** | `datetime`     | Datoen deltakeren er registrert i Arena. Det er litt ukjent hva som definerer en registrertDato i fremtiden når vi i Komet overtar opprettelsen av deltakere. Tidsstempel i ISO-8601 format.                                                   |
| **endretTidspunkt**     | `datetime`     | Tidsstempel for siste endring på deltakeren. Tidsstempel i ISO-8601 format.                                                                                                                                                                    |
| **kilde**               | `string`       | Kilde for deltakeren. Kan være `ARENA` eller `KOMET`. Hvis kilden er `KOMET` ble deltakeren opprettet i Komets nye løsning. Hvis kilde er `ARENA` ble deltakeren opprettet Arena.                                                              |
| **innhold**             | `object\|null` | Innhold for tiltaksdeltakelsen på strukturert format. Kun for deltakere som er opprettet hos Komet, eller som har fått lagt til innhold etter at Komet ble master for deltakeren.                                                              |
| **deltakelsesmengder**  | `list`         | Periodiserte deltakelsesmengder. Finnes kun på deltakere som Komet er master for, men gamle meldinger på topic vil kunne mangle dette feltet uavhengig av hvem som er master. Listen vil kun inneholde elementer for deltakarer på AFT og VTA. |

#### Status

| Felt                   | Format         | Beskrivelse                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
|------------------------|----------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **type**               | `string`       | En av følgende verdier: `VENTER_PA_OPPSTART`, `DELTAR`, `HAR_SLUTTET`, `IKKE_AKTUELL`, `FEILREGISTRERT`, `SOKT_INN`, `VURDERES`, `VENTELISTE`, `AVBRUTT`, `FULLFORT`, `PABEGYNT_REGISTRERING`, `UTKAST_TIL_PAMELDING`, `AVBRUTT_UTKAST` <br /><br /> Det er litt ulike typer statuser som kan settes på deltakere, basert på hvilke tiltak de deltar på. Hovedregelen er at `FULLFORT` og `AVBRUTT` kan kun settes på deltakere som går på tiltak hvor det er en felles oppstart eller at det er et opplæringstiltak, typisk kurs som `JOBBKLUBB`, `GRUPPE_ARBEIDSMARKEDSPPLAERING`, `GRUPPE_FAG_OG_YRKESOPPLAERING`, mens `HAR_SLUTTET` brukes kun på de andre tiltakene som har et "løpende" inntak og oppstart av deltakere. |
| **tekst**              | `string`       | Tekstrepresentasjon av statustypen (for visning).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| **aarsak**             | `object`       | Årsak for status til deltaker. Objektet er alltid til stede, men `type` og `beskrivelse` er `null` for statuser hvor årsak ikke er relevant (dvs. alle andre enn `HAR_SLUTTET`, `IKKE_AKTUELL`, `AVBRUTT` og `AVBRUTT_UTKAST`). Se [Aarsak](#aarsak)                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| **opprettetTidspunkt** | `datetime`     | Tidsstempel for når statusen ble opprettet. Tidsstempel i ISO-8601 format.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |

#### Aarsak

| Felt                   | Format         | Beskrivelse                                                                                                                                                                                                                                                                                                                                                                                                                            |
|------------------------|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **type**               | `string\|null` | En årsak kan finnes på enkelte typer statuser (`HAR_SLUTTET`, `IKKE_AKTUELL` og `AVBRUTT`) og er en av følgende verdier: `SYK`, `FATT_JOBB`, `TRENGER_ANNEN_STOTTE`, `FIKK_IKKE_PLASS`, `IKKE_MOTT`, `ANNET`, `AVLYST_KONTRAKT`, `UTDANNING`, `SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT`, `KRAV_IKKE_OPPFYLT`, `KURS_FULLT` I tillegg kan status `AVBRUTT_UTKAST` få årsaken `SAMARBEIDET_MED_ARRANGOREN_ER_AVBRUTT` i enkelte tilfeller. |
| **beskrivelse**        | `string\|null` | Tekstrepresentasjon av aarsakstypen (for visning).                                                                                                                                                                                                                                                                                                                                                                                     |

For mer informasjon om når og hvordan deltakerstatuser settes og endres se mer utdypende dokumentasjon
på [Confluence](https://confluence.adeo.no/pages/viewpage.action?pageId=573710206).

#### Deltakelsesinnhold

| Felt             | Format         | Beskrivelse                                                        |
|------------------|----------------|--------------------------------------------------------------------|
| **ledetekst**    | `string\|null` | Generell informasjon om tiltakstypen. Kommer fra Valp.             |
| **valgtInnhold** | `list`         | Liste over valgt innhold som gjelder denne deltakelsen. Kan være tom liste. |

#### Innhold

| Felt             | Format   | Beskrivelse                                                 |
|------------------|----------|-------------------------------------------------------------|
| **tekst**        | `string` | Tekstlig beskrivelse av innholdselementet. Kommer fra Valp. |
| **innholdskode** | `string` | Kodeverdi for innholdselementet. Kommer fra Valp.           |

#### Deltakelsesmengde

| Felt                   | Format        | Beskrivelse                                                   |
|------------------------|---------------|---------------------------------------------------------------|
| **deltakelsesprosent** | `float`       | Prosentandelen deltakeren opptar av en tiltaksplass.          |
| **dagerPerUke**        | `float\|null` | Antall dager deltakeren deltar på tiltaket per uke.           |
| **gyldigFraDato**      | `date`        | Dato f.o.m. når deltakalesesmengden trer i kraft.             |
| **opprettetTidspunkt** | `datetime`    | Når endringen ble opprettet. Tidsstempel i ISO-8601 format.   |

### Skjema

For oppdatert informasjon er det best å se siste versjon direkte:

- [DeltakerEksternV1Dto](https://github.com/navikt/amt-deltaker/blob/main/src/main/kotlin/no/nav/amt/deltaker/deltaker/kafka/dto/DeltakerEksternV1Dto.kt) (Skjema for deltakere)

```kotlin
data class DeltakerEksternV1Dto(
    val id: UUID,
    val gjennomforingId: UUID,
    val personIdent: String,
    val startDato: LocalDate?,
    val sluttDato: LocalDate?,
    val status: StatusDto,
    val registrertTidspunkt: LocalDateTime,
    val endretTidspunkt: LocalDateTime,
    val kilde: Kilde,
    val innhold: DeltakelsesinnholdDto?,
    val deltakelsesmengder: List<DeltakelsesmengdeDto>,
) {
    data class StatusDto(
        val type: DeltakerStatus.Type,
        val tekst: String,
        val aarsak: AarsakDto,
        val opprettetTidspunkt: LocalDateTime,
    )

    data class AarsakDto(
        val type: DeltakerStatus.Aarsak.Type?,
        val beskrivelse: String?,
    )

    data class DeltakelsesinnholdDto(
        val ledetekst: String?,
        val valgtInnhold: List<InnholdDto>,
    )

    data class InnholdDto(
        val tekst: String,
        val innholdskode: String,
    )

    data class DeltakelsesmengdeDto(
        val deltakelsesprosent: Float,
        val dagerPerUke: Float?,
        val gyldigFraDato: LocalDate,
        val opprettetTidspunkt: LocalDateTime,
    )
}

```
