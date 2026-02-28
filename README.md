# amt-deltakelser

Mono-repo for Team Komet sine Ktor-applikasjoner og fellesmodulen amt-lib.

## Innhold

- [Oversikt](#oversikt)
- [Applikasjoner](#applikasjoner)
    - [amt-deltaker](#amt-deltaker)
    - [amt-deltaker-bff](#amt-deltaker-bff)
    - [amt-distribusjon](#amt-distribusjon)
- [Fellesmoduler](#fellesmoduler)
    - [amt-lib](#amt-lib)
- [Bygg](#bygg)
- [CI/CD](#cicd)
- [Avhengigheter](#avhengigheter)
- [Lisens](#lisens)

---

## Oversikt

Dette monorepoet inneholder tre Ktor-applikasjoner som benytter en felles modul for modeller, 
databasekode og Kafka-integrasjon.

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

---

## Fellesmoduler

### amt-lib

Felleskode for alle applikasjonene:

- Datamodeller
- Databaseintegrasjon
- Kafka-produsenter og -konsumenter
- Generell hjelpekode og utilities

---

## Bygg
For å bygge alle modulene i **amt-deltakelser**, gjør følgende:

1. Klon repoet:

```bash
git clone git@github.com:navikt/amt-deltakelser.git
cd amt-deltakelser
```

Kjør build med Gradle Wrapper:
```bash
./gradlew build
```

For å fikse lint-feil og formatere koden i henhold til KtLint-reglene, kjør:
```bash
./gradlew ktlintFormat build
```

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