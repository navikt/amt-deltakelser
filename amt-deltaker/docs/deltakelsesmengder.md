# Periodiserte deltakelsesmengder

Deltakelsesmengder blir periodisert med en gyldig fra dato, for deltakere på Arbeidsforberedende Trening (AFT), og Varig Tilrettelagt Arbeid Skjermet (VTA).

Det betyr at en deltaker nå har en liste med deltakelsesmengder for hele deltakelsen, slik at man kan se hvor ofte en deltaker deltok på tiltaket på en gitt dato.

Når en deltaker har en startdato må veileder for deltakeren oppgi denne gyldig fra datoen når de endrer på deltakelsesmengde. De kan velge å sette den til hvilken som helst dato, innenfor deltakers start- og sluttdato, og den må være innenfor gjennomføringens start- og sluttdato.

Siden en periode av deltakelsesmengder er avhengig av start- og sluttdato på deltakeren så vil perioden også bli endret hvis veileder endrer på start- eller sluttdato.

Hvis en deltaker ikke har fått en startdato enda så er det ikke mulig for veileder å velge en gyldig fra dato på deltakelsesmengde, gyldig fra blir da automatisk satt til å være dagens dato, og perioden vil bli automatisk justert til å være gyldig fra startdato når det legges til en oppstartsdato.

Skjemaene våre for deltakere inneholder fra før en `dagerPerUke` og `deltakelsesprosent` eller `prosentStilling`, disse skal alltid reflektere hva enn som er deltakers nåværende deltakelsesmengde. Så når en deltakelsesmengde med en gyldig fra dato frem i tid blir registrert vil `dagerPerUke` og `deltakelsesprosent` først bli oppdatert på gyldig fra datoen. Mens listen `deltakelsesmengder` vil inneholde alle _gjeldende_ endringer i perioden, både fra fortid og fremtid. Endringer reflekteres umiddelbart i listen når de blir registrert.

Nye meldinger på topic `deltaker-v1` vil inneholde listen `deltakelsesmengder: List<DeltakelsesmengdeDto>`, men det er kun deltakere på Arbeidsforberedende Trening, og Varig Tilrettelagt Arbeid (Skjermet) hvor listen vil inneholde noen elementer, på de andre tiltakstypene vil listen være tom for nå (det er ikke mulig å sette deltakelsesmengde for disse i påmeldingsløsning i modia). Se [deltaker-v1 dokumentasjon](https://github.com/navikt/amt-tiltak/blob/main/.docs/deltaker-v1.md) for mer informasjon.

## Eksempler

For å vise alle mulige endringer følger vi et tenkt output på en kafkatopic hvor det blir produsert en melding hver gang det gjøres en endring på deltakeren.

Vi starter med en deltaker som blir meldt på et tiltak 01.12.2024, en forenklet modell for eksempelets skyld, som nettopp har blitt meldt på et tiltak:

**01.12.2024: Deltaker meldes på et tiltak**

Når deltakeren får status `UTKAST` eller `VENTER_PA_OPPSTART` så har den en deltakelsesmengde med gyldig fra dato samme dato som påmeldingen.

```kotlin
Deltaker(
    status = VENTER_PA_OPPSTART,
    startdato = null,
    sluttdato = null,
    deltakelsesprosent = 100,
    dagerPerUke = null,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2024-12-01",
            opprettet = "2024-12-01",
        ),
    ]
)
```

**02.12.2024: Startdato 10.12 legges til**

Startdato legges til, og gyldig fra dato på deltakelsesmengde oppdateres.

```kotlin
Deltaker(
    status = VENTER_PA_OPPSTART,
    startdato = "2024-12-10",
    sluttdato = "2025-02-10",
    deltakelsesprosent = 100,
    dagerPerUke = null,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-01",
        ),
    ]
)
```

**10.12.2024: En fremtidig deltakelsesmengde legges til**

Fra og med 15.12.2024 skal deltakeren delta 40% og 2 dager i uka.

```kotlin
Deltaker(
    ...
    deltakelsesprosent = 100,
    dagerPerUke = null,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-01",
        ),
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-15",
            opprettet = "2024-12-10",
        ),
    ]
)
```

**15.12.2024: Gyldig fra passeres**

En jobb kjører og deltakeren oppdateres automatisk med ny deltakelsesmengde 40% og 2 dager i uka.

```kotlin
Deltaker(
    ...
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-01",
        ),
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-15",
            opprettet = "2024-12-10",
        ),
    ]
)
```

**17.12.2024: Deltakelsesmengde endres tilbake i tid**

1. Veileder oppdager en feil, deltaker deltok ikke 100% fra 10.12.2024, men 90%. Siden det ikke er mulig å bestemme noen gyldig til dato, så har vi begrensede muligheter til å tolke hva som er intensjonen med en slik endring. Så vi må anta at når det kommer en endring med gyldig fra før noen andre endringer så må vi anta at den skal være gjeldende fra og med 10.12.2024 frem til deltakelsen avsluttes. Så det betyr at vi fjerner alle deltakelsesmengder som har en gyldig fra større eller lik den nye deltakelsesmengden.

```kotlin
Deltaker(
    ...
    deltakelsesprosent = 90,
    dagerPerUke = 5,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 90,
            dagerPerUke = 5,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-17",
        ),
    ]
)
```

2. Hvis det var riktig at deltakeren deltok 90% 10.12 også 40% fra og med 15.12, så må veileder utføre en endring igjen for å registrere den på nytt.

```kotlin
Deltaker(
    ...
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 90,
            dagerPerUke = 5,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-17",
        ),
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-15",
            opprettet = "2024-12-17",
        ),
    ]
)
```

**18.12.2024: Startdato endres frem i tid til 17.12.2024**

Siden denne startdatoen er større eller lik gyldig fra på deltakelsesmengde nr 2 for denne deltakeren, så er nå det første innslaget i deltakelsesmengder ikke lenger gyldig.

```kotlin
Deltaker(
    ...
    startdato = "2024-12-17"
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-17",
            opprettet = "2024-12-17",
        ),
    ]
)
```

**19.12.2024: Startdato tilbake i tid til 10.12.2024 igjen**

Når startdato endres tilbake i tid derimot så utvides deltakelsesmengden som allerede var gyldig ved forrige startdato til å ha en gyldig fra lik ny startdato.

```kotlin
Deltaker(
    ...
    startdato = "2024-12-10"
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-17",
        ),
    ]
)
```

Deltakelsesmengden på 90% som deltakeren opprinnelig stod oppført med de første dagene blir ikke tatt med videre fordi det ikke er tydelig at en slik endring av startdato også vil endre deltakelsesmengden for den perioden.

**02.01.2025: Fremtidig deltakelsesmengde legges til**

```kotlin
Deltaker(
    ...
    startdato = "2024-12-10"
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-17",
        ),
        Deltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2024-02-01",
            opprettet = "2024-01-02",
        ),
    ]
)
```

**03.01.2025: Sluttdato endres til 15.01.2024**

Siden sluttdatoen 15.01 er før den fremtidige deltakelsesmengden 01.02 så er den ikke lenger gyldig for denne perioden.

```kotlin
Deltaker(
    ...
    startdato = "2024-12-10"
    sluttdato = "2025-01-15"
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-17",
        ),
    ]
)
```

**05.01.2025: Sluttdato endres til 31.03.2024**

Siden sluttdatoen 31.03 er etter den fremtidige deltakelsesmengden 01.02 så er den nå gyldig igjen for denne perioden.

```kotlin
Deltaker(
    ...
    startdato = "2024-12-10"
    sluttdato = "2025-01-15"
    deltakelsesprosent = 40,
    dagerPerUke = 2,
    deltakelsesmengder = [
        Deltakelsesmengde(
            deltakelsesprosent = 40,
            dagerPerUke = 2,
            gyldigFra = "2024-12-10",
            opprettet = "2024-12-17",
        ),
        Deltakelsesmengde(
            deltakelsesprosent = 100,
            dagerPerUke = null,
            gyldigFra = "2024-02-01",
            opprettet = "2024-01-02",
        ),
    ]
)
```
