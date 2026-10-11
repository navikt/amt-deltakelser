# Periodiserte deltakelsesmengder

Deltakelsesmengder blir periodisert med en gyldig fra-dato for deltakere på Arbeidsforberedende trening (AFT) og varig tilrettelagt arbeid i skjermet virksomhet (VTA).

Det betyr at en deltaker har en liste med deltakelsesmengder for hele deltakelsen, slik at man kan se hvor ofte en deltaker deltok på tiltaket på en gitt dato. Periodene er sortert etter gyldig fra-dato. Hvis flere endringer har samme gyldig fra-dato, gjelder den sist opprettede. En periode uten endring i verdiene fra den foregående perioden vises ikke som en egen periode.

Når en deltaker har en startdato, må veilederen oppgi gyldig fra-dato når deltakelsesmengden endres. Datoen må være innenfor deltakerens start- og sluttdato og gjennomføringens start- og sluttdato.

Periodene avgrenses av deltakerens startdato. Hvis startdatoen flyttes frem, begynner første periode på den nye startdatoen. Hvis startdatoen flyttes tilbake, videreføres mengden som gjaldt ved forrige startdato fra den nye datoen. Senere perioder beholdes. Hvis startdatoen fjernes, fjernes startdatobegrensningen.

Hvis en deltaker ikke har fått en startdato ennå, kan veilederen ikke velge gyldig fra-dato. Datoen settes automatisk til dagens dato, og perioden justeres til startdatoen når den legges til.

Deltakerens felter `dagerPerUke` og `deltakelsesprosent` viser mengden som gjelder nå. En jobb oppdaterer disse feltene når en fremtidig gyldig fra-dato passeres. En tilbakedatert endring oppdaterer dem bare hvis den endrer mengden som gjelder nå. Den periodiserte listen viser både historiske og fremtidige endringer umiddelbart etter at de er registrert.

I modellen og veilederresponsen kan `deltakelsesprosent` og `dagerPerUke` være `null` uavhengig av hverandre. En deltakelsesmengde tas med fra vedtak eller Arena-import når minst ett av feltene har en verdi; den andre verdien forblir `null`. I `deltaker-v1`-meldingen settes manglende deltakelsesprosent fortsatt til 100 % av hensyn til den eksisterende kontrakten.

Nye meldinger på topic `deltaker-v1` inneholder listen `deltakelsesmengder: List<DeltakelsesmengdeDto>`. Listen avgrenses av deltakerens start- og sluttdato når disse er satt, og vil bare inneholde elementer for deltakere på AFT og VTA; for andre tiltakstyper vil den være tom. Se [deltaker-v1-dokumentasjonen](https://github.com/navikt/amt-tiltak/blob/main/.docs/deltaker-v1.md) for mer informasjon.

Veilederresponsen har også listen `gyldigeDeltakelsesmengder`, med alle perioder fra og med deltakerens startdato. Listen avgrenses ikke av sluttdatoen, slik at senere perioder blir med. Feltene `deltakelsesprosent` og `dagerPerUke` er nullable også i denne responsen. Den eldre responsen `deltakelsesmengder` beholdes midlertidig.

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

1. Veilederen oppdager at deltakeren ikke deltok 100 % fra 10.12.2024, men 90 %. Endringen erstatter derfor mengden fra 10.12.2024. Senere perioder med en senere gyldig fra-dato beholdes.

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
            opprettet = "2024-12-10",
        ),
    ]
)
```

Perioden på 40 % fra 15.12 beholdes automatisk, slik at veilederen ikke trenger å registrere den på nytt.

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
            gyldigFra = "2025-02-01",
            opprettet = "2025-01-02",
        ),
    ]
)
```

**03.01.2025: Sluttdato endres til 15.01.2025**

Siden sluttdatoen 15.01 er før den fremtidige deltakelsesmengden 01.02, vises ikke den fremtidige perioden mens denne sluttdatoen gjelder.

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

**05.01.2025: Sluttdato endres til 31.03.2025**

Fordi sluttdatoen 31.03 er etter den fremtidige deltakelsesmengden 01.02, vises perioden igjen.

```kotlin
Deltaker(
    ...
    startdato = "2024-12-10"
    sluttdato = "2025-03-31"
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
            gyldigFra = "2025-02-01",
            opprettet = "2025-01-02",
        ),
    ]
)
```
