# amt-deltakelser

Monorepo for Team Komets backend-applikasjoner, fellesmoduler og infrastruktur.

## Innhold

- [Oversikt](#oversikt)
- [Applikasjoner](#applikasjoner)
    - [amt-aktivitetskort-publisher](#amt-aktivitetskort-publisher)
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
    - [amt-bigquery-terraform](#amt-bigquery-terraform)
- [Bygg](#bygg)
- [CI/CD](#cicd)
- [Avhengigheter](#avhengigheter)
- [Lisens](#lisens)

---

## Oversikt

Applikasjonene bruker Kotlin med Ktor eller Spring Boot. Fellesmodulene inneholder blant annet datamodeller,
API-kontrakter og teststøtte. Infrastruktur for Nais og BigQuery forvaltes i egne kataloger.

---

## Applikasjoner

### amt-aktivitetskort-publisher

**amt-aktivitetskort-publisher** oppretter og oppdaterer aktivitetskort for tiltaksdeltakelser i aktivitetsplanen.
Applikasjonen er bygget på Spring Boot.

- Leser deltaker-, gjennomførings- og arrangørdata fra Kafka og supplerer med oppslag mot andre tjenester.
- Lagrer data og meldinger lokalt og publiserer aktivitetskort til AKaaS (Aktivitetskort som en tjeneste).
- Oppdaterer kortene når deltakelsen, gjennomføringen eller arrangøren endres.

Se [modulens README](amt-aktivitetskort-publisher/README.md) for byggkommandoer.

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
en deltakeroversikt og lar dem følge opp deltakere på sine tiltak. Applikasjonen er bygget på Spring Boot.

- Leser deltaker-, arrangør- og ansattdata fra Kafka og lagrer dem i en lokal database.
- Tilbyr API-er for arrangørenes veiledere og koordinatorer.
- Publiserer forslag, endringer og vurderinger fra arrangørene på Kafka.

Se [modulens README](amt-tiltaksarrangor-bff/README.md).

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

Endringer her deployes via [amt-iac-workflowen](.github/workflows/ci-deploy-iac.yml).

### amt-bigquery-terraform

**amt-bigquery-terraform** forvalter Datastream, BigQuery-datasett, views og tilganger med Terraform.
Datastream replikerer data fra Cloud SQL til BigQuery for analyse og rapportering, blant annet i Metabase.

- Prod har Datastream og views for `amt-deltaker` og `amt-distribusjon`.
- Konfigurasjonen er delt mellom `dev/`, `prod/` og gjenbrukbare moduler i `modules/`.
- Datastream for `amt-deltaker` i dev er deaktivert som standard, men Terraform forvalter fortsatt fellesressurser der.

Modulen inngår ikke i Gradle-bygget. Se [modulens README](amt-bigquery-terraform/README.md) for
innlogging, Terraform-kommandoer, Metabase-tilgang og backfill.

**Endringer i denne katalogen som merges til `main`, starter automatisk Terraform apply i både dev og prod.
Se gjennom planene før merge.**

---

## Bygg
Gradle-modulene krever Java 25. Testene som bruker Testcontainers, trenger Docker.
Terraform-konfigurasjonen i `amt-bigquery-terraform` bygges og valideres separat.

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

Gradle gjenbruker byggresultater fra cache når kildekode og avhengigheter er uendret.

---

## CI/CD

**amt-deltakelser** har automatiserte workflows for testing, sikkerhet og publisering, basert på **GitHub Actions**.

### Test og bygg

- [Test-workflowen](.github/workflows/ci-run-tests.yml) kjører ved push til brancher utenom `main`.
  Den velger berørte moduler, inkludert Spring Boot-applikasjonene, og tester alle Gradle-modulene ved endringer i felleskode.
- Enhetstester og integrasjonstester kjøres med **JUnit 5**, **Kotest** og **Testcontainers**.
- KtLint sjekker kodeformat og stil automatisk.
- Filtre på endrede filer begrenser hvilke moduler som bygges og testes.

### Sikkerhet

- [CodeQL](.github/workflows/ci-codeql-analysis-kotlin.yml) analyserer Kotlin-koden ved relevante PR-er og push til
  `main`, ved merge queue og i en ukentlig kjøring.
- Funn vises i GitHubs code scanning.

### Publisering

- [Applikasjons-workflowen](.github/workflows/ci-deploy-app.yml) bygger og deployer berørte applikasjoner ved
  push til `main`, inkludert `amt-aktivitetskort-publisher` og `amt-tiltaksarrangor-bff`.
- [`amt-lib`-workflowen](.github/workflows/amt-lib-release.yml) publiserer Maven-pakker til GitHub Packages ved
  relevante endringer på `main` eller manuell kjøring.
- Nais-ressurser og Terraform-konfigurasjon har egne workflows, uavhengig av Gradle-bygget.

### Terraform

[Terraform-workflowen](.github/workflows/terraform.yml) kjører ved push med endringer i `amt-bigquery-terraform/`.
Den kontrollerer formatering, validerer konfigurasjonen og lager planer for dev og prod.
På `main` kjører den deretter apply i begge miljøer. Brancher med prefikset `dev-` kjører apply bare i dev.
Andre brancher lager planer uten apply.

---

## Avhengigheter

Vi bruker [Gradle convention plugins](buildSrc/src/main/kotlin) for felles oppsett av Kotlin, KtLint og avhengigheter.
Ktor-applikasjonene bruker `amt-ktor-conventions`, mens `amt-aktivitetskort-publisher` og
`amt-tiltaksarrangor-bff` bruker `amt-spring-conventions`.

### Hovedbiblioteker og bundles

- **Ktor**: HTTP-server og klient via bundlene `ktor-server` og `ktor-client`.
- **Spring Boot**: Spring MVC med Jetty, Spring JDBC, Kafka, RestClient og Actuator.
  Felles Spring Boot-avhengigheter legges til i [Spring-konvensjonen](buildSrc/src/main/kotlin/amt-spring-conventions.gradle.kts).
- **Kafka**: Ktor-applikasjonene bruker Kafka- og outbox-støtte fra `amt-felles`.
  Spring Boot-applikasjonene bruker Spring Boot Kafka og outbox fra `nav-common-kafka`.
- **Database**: Ktor bruker HikariCP, Flyway, PostgreSQL og Kotliquery via `database`.
  Spring Boot bruker Spring JDBC med `NamedParameterJdbcTemplate`, Flyway og PostgreSQL.
- **Logging**: Logback, Logstash-encoder og nav-common-log via `logging` for Ktor.
  Spring Boot bruker sitt logging-oppsett med Logstash-encoder og nav-common-log i tillegg.
- **Metrics**: Micrometer og Prometheus, med Actuator i Spring Boot-applikasjonene.
- **Testing**: Ktor bruker `amt-felles:ktor-test` og `ktor-test`.
  Spring Boot bruker Spring Boot-testmoduler og SpringMockK. Kotest, MockK og Testcontainers brukes også.
- **Fellesmoduler**: `amt-lib` inneholder modeller, hjelpekode, Spring-støtte og teststøtte.
  `amt-felles` inneholder intern felleskode, blant annet Ktor-, Kafka- og outbox-støtte.
- **Kodekvalitet**: KtLint brukes via convention plugins for formatering og kodekontroll.

### Bundles brukt i convention plugins

- `ktor-server` → alle nødvendige Ktor-server-moduler
- `ktor-client` → Ktor HTTP-klientmoduler
- `database` → Hikari, Flyway, PostgreSQL og Kotliquery
- `logging` → Logback, Logstash encoder og nav-common-log
- `ktor-test` → Ktor test-host og klient-mock
- `kotest` → Kotest assertions

Spring Boot-oppsettet bruker ikke egne bundles. Spring-avhengighetene legges til direkte i Spring-konvensjonen,
mens applikasjonsspesifikke avhengigheter ligger i modulenes `build.gradle.kts`.

Versjonskatalogen ligger i [gradle/libs.versions.toml](gradle/libs.versions.toml).
Spring Boot-versjonen styres der, mens Spring Boot BOM styrer versjonene på avhengighetene den forvalter.

---

## Lisens

Dette prosjektet er lisensiert under **MIT License**.  
Se [LICENSE](LICENSE) for fullstendig lisensinformasjon.
