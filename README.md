# amt-deltakelser

Monorepo for Team Komet sine backend-applikasjoner (Ktor og Spring Boot) og fellesmodulen amt-lib.

## Innhold

- [Oversikt](#oversikt)
- [Applikasjoner](#applikasjoner)
    - [amt-deltaker](#amt-deltaker)
    - [amt-deltaker-bff](#amt-deltaker-bff)
    - [amt-distribusjon](#amt-distribusjon)
    - [amt-tiltaksarrangor-bff](#amt-tiltaksarrangor-bff)
    - [amt-pdfgen](#amt-pdfgen)
- [Fellesmoduler](#fellesmoduler)
    - [amt-lib](#amt-lib)
    - [amt-felles](#amt-felles)
- [Infrastruktur](#infrastruktur)
    - [amt-iac](#amt-iac)
- [Bygg](#bygg)
- [CI/CD](#cicd)
- [Avhengigheter](#avhengigheter)
- [Lisens](#lisens)

---

## Oversikt

Dette monorepoet samler koden Team Komet jobber mest med — applikasjoner, fellesmoduler og infrastruktur.

---

## Applikasjoner

### amt-deltaker

**amt-deltaker** er den viktigste applikasjonen i arkitekturen og fungerer som kjerne for deltakerdata.

Hovedfunksjoner:

- Publiserer siste versjon av deltakelser på Kafka
- Fungerer som ressurs-server for **amt-deltaker-bff**
- Har en database som inneholder “sannheten” om deltakerne

### amt-deltaker-bff

Backend-for-frontend for flere flate-applikasjoner.  

### amt-distribusjon

**amt-distribusjon** har ansvar for journalføring av vedtak og å varsle tiltaksdeltaker og veileder om viktige endringer.

- Nav-veileder varsles gjennom OBO sitt filter i **Modia Arbeidsrettet oppfølging**.
- Deltaker varsles gjennom minside-varsel på **nav.no**.
- Ikke-digitale deltakere mottar varsler som brev.

Denne applikasjonen håndterer også Kafka-meldinger og asynkrone prosesser for å sikre at varsler og journalføring 
skjer på en pålitelig måte.

### amt-tiltaksarrangor-bff

**amt-tiltaksarrangor-bff** er backend-for-frontend for **amt-tiltaksarrangor-flate**, som gir tiltaksarrangører
en deltakeroversikt og lar dem følge opp deltakere på sine tiltak. I motsetning til de andre applikasjonene i
monorepoet er denne bygget på **Spring Boot**.

### amt-pdfgen

**amt-pdfgen** genererer PDF-er av vedtak, endringsvedtak og brev som **amt-distribusjon** journalfører og distribuerer.

- Bygger på [pdfgen-core](https://github.com/navikt/pdfgen-core) og bruker Handlebars-maler.
- Maler ligger under `templates/amt/` og testdata under `data/amt/`.
- En standardmal dekker de fleste tiltakstypene, mens enkelte tiltak (f.eks. VTA og kurs) har egne maler.
- Eksponerer endepunkter pr. mal som kan brukes både av andre applikasjoner og til lokal forhåndsvisning under utvikling.

---

## Fellesmoduler

### amt-lib

Felleskode publisert som Maven-pakke til GitHub Packages, og brukt av applikasjoner også utenfor dette monorepoet:

- Datamodeller
- Generell hjelpekode og utilities
- Teststøtte

### amt-felles

Intern felleskode brukt av applikasjonene i dette monorepoet (publiseres ikke som Maven-pakke):

- **`intern-api-kontrakter/`** – Delte request/response-modeller for intern-API mellom applikasjonene.
- **`kafka/`** – Felles Kafka-produsenter, -konsumenter og outbox-mønster.
- **`ktor/`** – Felles Ktor-oppsett, plugins og hjelpekode for HTTP-server og -klient.
- **`ktor-test/`** – Teststøtte for Ktor-applikasjoner, inkludert Testcontainers-oppsett for Postgres og Kafka.

---

## Infrastruktur

### amt-iac

**amt-iac** (Infrastructure as Code) inneholder konfigurasjon for plattform-ressurser brukt av Komet-applikasjonene
i Nais (GCP). Strukturen er delt opp etter ressurstype og miljø (`dev`/`prod`):

- **`alerts/`** – Prometheus alert-regler og varslingskonfigurasjon.
- **`kafka-manager/`** – Konfigurasjon av Kafka-brukere og tilganger (Aiven).
- **`kafka-topic/`** – Definisjoner av Kafka-topics som eies av Team Komet.

Endringer her rulles ut via egne workflows i `amt-iac`-katalogen.

---

## Bygg
For å bygge alle modulene i **amt-deltakelser**, gjør følgende:

Klon repoet:

```bash
git clone git@github.com:navikt/amt-deltakelser.git
cd amt-deltakelser
```

Kjør deretter build med Gradle Wrapper:
```bash
./gradlew build
```

For å fikse lint-feil og formatere koden i henhold til KtLint-reglene, kjør:
```bash
./gradlew ktlintFormat build
```

For å bygge enkeltmoduler, f.eks. `amt-deltaker-bff`:
```bash
./gradlew build -p amt-deltaker-bff
```
eller
```bash
./gradlew :amt-deltaker-bff:build
```

Bygging av hele repoet skal normal gå raskt etter første bygg grunnet caching. Ved bumping av versjoner, vil alt bygges på nytt.

---

## CI/CD

**amt-deltakelser** har automatiserte workflows for testing, sikkerhet og publisering, basert på **GitHub Actions**.

### Test og bygg

- Alle Ktor-applikasjoner og `amt-lib` bygges og testes automatisk ved push eller pull request.
- Enhetstester og integrasjonstester kjøres med **JUnit 5**, **Kotest** og **Testcontainers**.
- KtLint sjekker kodeformat og stil automatisk.
- Workflows trigges per modul via `paths` og `paths-ignore` for å redusere unødvendige bygg.

### Sikkerhet

- **CodeQL** kjøres automatisk på alle PRer for å identifisere sikkerhetsrisikoer og sårbarheter i koden.
- Eventuelle funn markeres som kommentarer i PR.

### Publisering

- `amt-lib` publiseres som Maven-pakke til GitHub Packages.
- Publisering skjer **når en PR merges til `main`**, eller ved commits som pushes direkte til `main`.

---

## Avhengigheter

Applikasjonene i **amt-deltakelser** benytter flere viktige biblioteker og fellesmoduler. 
Vi bruker [**Gradle convention plugins**](buildSrc/src/main/kotlin) for å standardisere oppsettet, inkludert Kotlin, 
Ktor og KtLint, slik at alle applikasjoner følger samme konvensjoner.

### Hovedbiblioteker og bundles

- **Ktor** – HTTP-server og klient; alle Ktor-moduler håndteres via `ktor-server` og `ktor-client` bundles.
- **Kafka** – Kafka-klienter og felles outbox-mønster via `amt-lib`.
- **Database** – HikariCP, Flyway, PostgreSQL og Kotliquery samlet i `database` bundle.
- **Logging** – Logback, Logstash-encoder og nav-common-log, samlet i `logging` bundle.
- **Metrics** – Micrometer + Prometheus for overvåkning og eksponering av metrics.
- **Testing** – Ktor-test, Kotest, Mockk og Nimbus JWT, samlet i `ktor-test` og `kotest` bundles.
- **Fellesmoduler (amt-lib)** – inneholder modeller, utilities, outbox/Kafka-støtte og teststøtte.
- **Kodekvalitet** – KtLint brukes via convention plugin for automatisk formatering og kodekontroll.

### Bundles brukt i convention plugins

- `ktor-server` → alle nødvendige Ktor-server-moduler
- `ktor-client` → Ktor HTTP-klientmoduler
- `database` → Hikari, Flyway, PostgreSQL og Kotliquery
- `logging` → Logback, Logstash encoder og nav-common-log
- `ktor-test` → Ktor test-host og klient-mock
- `kotest` → Kotest assertions

Alle versjoner styres i **libs.versions.toml**, slik at alle moduler i monorepoet har konsistente versjoner.

---

## Lisens

Dette prosjektet er lisensiert under **MIT License**.  
Se [LICENSE](LICENSE) for fullstendig lisensinformasjon.

