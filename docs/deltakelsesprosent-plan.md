# Plan for visning av veilederregistrert deltakelsesprosent

## Status ved pause 23. november 2026

**Neste arbeidsøkt starter i `amt-deltaker-flate`.** Modeller, `amt-deltaker` og `amt-deltaker-bff` er tilpasset lesing av nullable prosent. Frontend er ikke endret og støtter ennå ikke den nye kontrakten.

Denne statusdelen beskriver det som er gjort og de siste avklaringene. Helhetsplanen lenger ned inneholder også arbeid som ikke er startet. Ikke tolk nullable prosent som dokumentasjon av at verdien er veilederregistrert.

### Gjennomført

| Område | Status |
|---|---|
| Delte modeller og intern HTTP-kontrakt | `Deltakelsesmengde.deltakelsesprosent` og tilsvarende response-felt er `Float?`. Ordinær konvertering av endringshendelser setter ikke lenger inn 100 %. |
| Vedtak og Arena-import | Gir en periode når minst én av prosent og dager er registrert. Begge null gir ingen periode. |
| Eksplisitte mengdeendringer | Bevarer også daterte perioder med begge verdier null. En slik endring kan fjerne tidligere registrert mengde. |
| `amt-deltaker` | HTTP-mapping og beregning ved endret startdato bevarer null. Senere behandling av fremtidige endringer får ikke lenger kunstig 100 % fra domenekonverteringen. |
| `amt-deltaker-bff` | Veileder- og innbyggerrespons viderefører nullable container uten tomt erstatningsobjekt. Fallback til 100 % er fjernet fra sammenligningen ved mengdevalidering. |
| Kafka V1 til Arena og arrangør-BFF | Eksisterende oppførsel beholdes gjennom en egen kompatibilitetssti. Minimale arrangørtilpasninger er godkjent for dette formålet, ikke for ny funksjonalitet. |
| Aktivitetskort og distribusjon | Bare testdata er tilpasset den endrede kontrakten. Ingen funksjonell visningsendring er gjort her. |

`toDeltakelsesmengderMedFallback()` i `DeltakelsesmengdeLegacy.kt` bruker den samme tidslinjebyggeren som ordinær konvertering, men med tidligere konverteringsregler. Endringshendelser får fortsatt 100 % ved manglende prosent i denne stien, og vedtak/import uten prosent utelates. Reglene brukes **før normalisering**, slik at også sammenslåing av perioder blir som før. Fallback bare i den endelige Kafka-mappingen ville ikke bevart dette.

Ekstern Kafka V1 beholder eksisterende konvertering og sentinel-håndtering. Det er ikke innført nye topics eller endret Kafka-kontrakter.

### Avklart kontrakt og oppførsel

- `deltakelsesmengder` er null når den beregnede og avgrensede tidslinjen er tom. Ingen periode skal konstrueres fra toppnivåfeltene for å fylle dette hullet.
- Når containeren finnes, er `sisteDeltakelsesmengde` påkrevd og ikke-null, uten standardverdi. `nesteDeltakelsesmengde` er fortsatt nullable.
- `sisteDeltakelsesmengde` er den kronologisk siste gyldige perioden, også om den ligger frem i tid. `nesteDeltakelsesmengde` er første fremtidige periode. De kan peke på samme periode.
- Gjeldende mengde vises fra deltakerens toppnivåfelter. `sisteDeltakelsesmengde` brukes som sammenligningsgrunnlag i endringsmodalen, ikke som gjeldende visningsverdi.
- Vedtak og Arena-import med bare dager skal beholdes. Vedtak og import med begge verdier null skal utelates. Dette må ikke overføres til eksplisitte endringshendelser, der begge null fortsatt må kunne erstatte tidligere verdier.
- Prosent forblir `Float?`. Avrunding til heltall er uttrykkelig utenfor oppgaven.
- Eksisterende redigeringsmuligheter skal fungere videre. Ikke åpne prosentregistrering for enkeltplass eller flere tiltakstyper, og ikke endre eksisterende skjemavalg eller verdi-/datogrenser som del av lesetilpasningen.
- Tester i denne PR-en skal ha Arrange/Act/Assert. Flere tester av samme metode grupperes med `@Nested inner class`. Bruk norske testnavn og kommentarer, og skriv Arena med stor A i kommentarer.

### Enkeltplass og feilen i tidligere logikk

Nye Komet-enkeltplasser lagres med null prosent. `EnkeltplassService.opprettKladd` bruker `DeltakerKladdUpsertDbo`, der prosent har standardverdien null. `DeltakerRepository.upsertKladd` binder verdien direkte til SQL. `updateEnkeltplass` oppdaterer ikke prosentfeltet, og vedtakssnapshotet kopierer deltakerens verdi uten fallback.

Tidligere utelot `Vedtak.toDeltakelsesmengde()` et vedtak med null prosent, selv om dager var registrert. Visningen og modalens forhåndsutfylling fungerte likevel fordi de brukte toppnivåfeltene. Manglende `sisteDeltakelsesmengde` lot «ingen reell endring»-sjekken slippe gjennom uten sammenligningsgrunnlag. Etter første endringshendelse fantes en periode, men domenekonverteringen ga den kunstig 100 %. Enkeltplassens visning og frontend-sammenligning brukte bare dager og skjulte derfor prosenten.

Nå bidrar vedtakets registrerte dager fra starten, og senere endringshendelser beholder null prosent. Enkeltplass bruker samme endringsflyt og samme siste periode som gruppetiltak, men frontend sammenligner bare dager. Tomt dagfelt er allerede tillatt og kan gi en eksplisitt endring med begge verdier null. Dette er grunnen til skillet mellom snapshots og endringshendelser.

Feilmekanismene er påvist i koden. Omfanget i produksjonsdata er ikke undersøkt.

### Neste arbeidsøkt

1. Les gjeldende instrukser og arbeidsområdets diff før videre endringer. Det finnes både stagede og ustagede endringer; ikke overskriv eller tilbakestill brukerens arbeid.
2. Fortsett i `amt-deltaker-flate`, først med skjemaer og typer. Periodens prosent må tillate null, containeren må tillate null, og siste periode må være påkrevd når containeren finnes.
3. Tilpass eksisterende endringsmodalers sammenligningshjelper til nullable container, uten å utvide redigering. `harEndringSidenSisteDeltakelsesmengde` leser i dag containeren direkte.
4. Fjern 100 %-fallback i lesing og formattering. Behold dager uten prosent, skjul mengdetekst når begge verdier mangler, og håndter en tom fremtidig endringsperiode uten å vise en tom mengdeseksjon.
5. Skill visningsregler fra registreringsregler før tiltakstype-/enkeltplassfiltre endres. Delte frontendkomponenter må ikke endre arrangørens oppførsel som bieffekt.
6. Fortsett med målrettede tester i eksisterende testoppsett. Avklar videre omfang før arbeidet utvides til registreringsmetadata, brev/PDF-er eller aktivitetskort.

Start med disse frontendfilene:

- `packages/deltaker-flate-common/model/deltaker.ts`
- `packages/deltaker-flate-common/utils/displayText.ts`
- `packages/deltaker-flate-common/components/DeltakelsesmengdeInfo.tsx`
- `apps/nav-veileders-flate/src/api/data/deltaker.ts` og innbyggerflatens tilsvarende kontrakt
- `apps/nav-veileders-flate/src/components/tiltak/endre-deltakelse-modaler/endre-deltakelsesmengde/utils.ts`

Dagens frontend krever fortsatt en ikke-null container og numerisk prosent i hver periode. Backend og BFF er derfor ikke klare for utrulling mot uendret frontend.

### Gjenstående fra helhetsplanen

Kildevurdert visningsgrunnlag, metadata som dokumenterer eksplisitte veiledervalg og historisk opprydding er ikke implementert. Lagrede 100 %-verdier er ikke omskrevet, og nullstøtten beviser ikke opprinnelsen til eksisterende prosenter. Frontend, brev/PDF-er og aktivitetskort har ennå ikke fått den nye visningsoppførselen.

### Kontroller og praktiske hensyn

Siste kontroll etter Arena-justeringen kjørte `:amt-lib:models:check`, målrettede `:amt-deltaker:test`, `:amt-deltaker:ktlintCheck` og `:amt-tiltaksarrangor-bff:compileTestKotlin` uten feil. Backendutvalget dekket `DeltakerResponseBuilderTest`, `DeltakerKafkaPayloadBuilderTest`, `veileder.endring.extensions.*` og `DeltakelsesmengdeUpdateTest`.

Internkontraktens `check`, BFF-ens mapping-/valideringstester og ktlint samt kompilering av testkoden i distribusjon og aktivitetskort-publisher ble også kjørt etter at siste periode ble påkrevd. Hele monorepoets `check` er ikke kjørt. Dette kreves før en PR med endringer i delte moduler opprettes eller oppdateres.

Bruk prosjektets JDK 25. MockK krevde forhåndslasting av den eksisterende Byte Buddy-agenten i `JAVA_TOOL_OPTIONS`; ingen avhengigheter eller bygginnstillinger ble endret:

```bash
JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -javaagent:$HOME/.gradle/caches/modules-2/files-2.1/net.bytebuddy/byte-buddy-agent/1.18.2/62f38a6faf7f069d661b79a07d566f504b0b20c4/byte-buddy-agent-1.18.2.jar" \
./gradlew :amt-lib:models:check :amt-deltaker:test \
  --tests 'no.nav.amt.deltaker.api.DeltakerResponseBuilderTest' \
  --tests 'no.nav.amt.deltaker.kafka.dto.DeltakerKafkaPayloadBuilderTest' \
  --tests 'no.nav.amt.deltaker.veileder.endring.extensions.*' \
  --tests 'no.nav.amt.deltaker.veileder.DeltakelsesmengdeUpdateTest' \
  :amt-deltaker:ktlintCheck :amt-tiltaksarrangor-bff:compileTestKotlin \
  --no-daemon --no-configuration-cache --console=plain
```

Kommandoen kjøres fra `amt-deltakelser`. Tilgang til Docker-oppsettet var tidligere blokkert i denne økten; de målrettede regresjonstestene over krever ikke Docker. Ikke omgå tilgangsbegrensninger for å kjøre DB-integrasjonstester.

## Mål og avgrensning for helhetsplanen

**Vis dokumentert veilederregistrert deltakelsesprosent uavhengig av tiltakstype. Manglende eller udokumentert prosent skal aldri erstattes med 100 %.**

Helhetsplanen bygger på [kartleggingen av dagens løsning](deltakelsesprosent-oversikt.md) og avklaringene 23. november 2026. Den er ikke ferdig implementert; se statusdelen over for gjennomført arbeid og gjeldende avgrensning.

| Innenfor | Utenfor |
|---|---|
| Nav-veileders flate og innbyggers flate | Hele arrangørsporet, inkludert arrangørfrontend og `amt-tiltaksarrangor-bff` |
| Utkast, avbrutte utkast, detaljer og historikk i disse flatene | Nye registrerings- eller endringsmuligheter for flere tiltakstyper |
| `amt-deltaker`, `amt-deltaker-bff` og nødvendige interne kontrakter | Endring av status-, låse-, tilgangs- eller godkjenningsregler |
| Brev/PDF-grunnlag i `amt-distribusjon` og nødvendige maler i `amt-pdfgen` | Omskriving av arkiverte brev eller nye utsendinger av gamle brev |
| Aktivitetskort og nødvendige oppdateringer i publisheren | Å erstatte, slette eller korrigere rådata fra Arena som del av visningsendringen |
| Fjerning av automatisk 100 % i eksisterende registreringsskjemaer | Nye tjenester, nye topics eller endret Nais-/auth-oppsett |

«Alle tiltakstyper» gjelder **visning**, også for enkeltplasser. Det betyr ikke at alle tiltak får registreringsfelt. En tiltakstype uten dokumentert prosent får fortsatt ingen prosent i visningen.

## Avtalt oppførsel

| Datagrunnlag | Visning |
|---|---|
| Dokumentert veilederregistrert `80`, uten dager | `80 %` |
| Dokumentert veilederregistrert `100`, uten dager | `100 %` |
| Dokumentert veilederregistrert `80`, med tre dager | `80 % fordelt på 3 dager i uka` |
| Ingen dokumentert prosent, men tre registrerte dager | `3 dager i uka` |
| Verken dokumentert prosent eller registrerte dager | Skjul hele mengdeseksjonen |
| Råverdi `100` uten dokumentert veiledervalg | Skjul prosenten. Behold eventuelle registrerte dager. |
| Prosent fra Arena uten senere dokumentert veilederregistrering | Skjul prosenten. Behold eventuelle registrerte dager. |
| Fremtidig dokumentert prosent | Vis i riktig fremtidig periode, ikke som gjeldende før eksisterende gyldighetsregler tilsier det |
| Historikkhendelse uten dokumentert prosent | Skjul prosenten i den hendelsen, men behold hendelsen og øvrige opplysninger |

Reglene er like for ordinære plasser og enkeltplasser. Gyldig prosent må også oppfylle eksisterende verdi-/presisjonsregler. `0` og verdier utenfor gyldig intervall skal ikke få 100 %-fallback.

Registrerte dager skal ikke regnes om til prosent. Fjerning av tiltakstypefilter for prosent skal heller ikke endre eksisterende valideringsgrenser for dager per uke.

## Hovedutfordringen er dokumentasjon av kilden

En ikke-null verdi i databasen er ikke tilstrekkelig bevis. I dagens påmelding blir 100 %-valget forhåndsvalgt når prosent mangler, og request-byggeren gjør dette valget om til `100`. Endringsmodalen har også `deltakelsesprosent ?? 100`. [K1], [K2], [K3]

`Vedtak` og `DeltakerEndring` har informasjon om hvem som registrerte handlingen, men ikke et eget felt som dokumenterer et eksplisitt prosentvalg. Et Nav-opprettet vedtak, `kilde = KOMET`, en bestemt dato eller selve tallet `100` beviser derfor ikke alene at prosenten var valgt av veileder. [K4], [K5]

**Konsekvens:** Planen kan ikke love at alle eldre, reelt veilederregistrerte prosenter kan beholdes. Verdier uten tilstrekkelig dokumentasjon behandles som ikke registrert. Historisk tilbakefylling tillates bare der en konkret registreringsvei kan dokumenteres, ikke ved gjetning ut fra verdien.

## Anbefalt løsning

La `amt-deltaker` produsere et eget, kildevurdert visningsgrunnlag. Behold dagens råverdier og legacy-kontrakter der andre konsumenter trenger dem. Dette gir én felles vurdering for flatene og aktivitetskort, uten at hver konsument må gjette på opprinnelse.

Et eget visningsgrunnlag krever flere felt og mappinger enn å fjerne `?? 100` i frontend. Alternativet avvises fordi det fortsatt ville vist importerte og automatisk lagrede prosenter som veilederregistrerte, og fordi direkte endring av delte legacy-funksjoner kan påvirke arrangørsporet.

### Dokumenter nye eksplisitte valg

Registreringsmetadata må være knyttet til **den konkrete verdien og registreringen**, ikke bare til deltakeren:

1. Eksisterende prosentvalg starter uten automatisk «Ja» når dokumentert verdi mangler. Veilederen må velge 100 % eller angi en annen prosent i registreringsflyter som allerede har feltet.
2. Endringsmodaler skal ikke fylle inn 100 % fordi prosent mangler. En udokumentert råverdi skal heller ikke automatisk bli en dokumentert verdi ved lagring av en annen opplysning.
3. Ny request-kontrakt skiller mellom eksplisitt prosentvalg og manglende valg. Backend knytter dokumentasjonen til den autoriserte veilederhandlingen. Gamle requests uten slik dokumentasjon behandles som ukjent, ikke som eksplisitte.
4. Dokumentasjonen lagres sammen med nåverdi/kladd og relevante vedtaks- og endringssnapshots. Nye metadata er nullable for eldre data. Behov for nye kolonner håndteres med additive Flyway-migrasjoner, uten tilbakefylling av antatte verdier.
5. Kladd, deling av utkast og senere godkjenning bevarer dokumentasjonen. Innbyggers godkjenning skal ikke i seg selv gjøre en udokumentert prosent til veilederregistrert.

Dette utvider ikke hvilke tiltakstyper som kan registrere prosent. Håndtering av tomt valg følger kontrakten for den eksisterende flyten; det skal aldri tolkes som 100 %.

### Skill visningsgrunnlag fra råverdi og driftslogikk

Foreslått internt felt er `deltakelsesmengdeVisning`, med nåværende og neste periode. Hver periode inneholder nullable, dokumentert `deltakelsesprosent`, eksisterende `dagerPerUke` og nødvendig gyldighetsdato. Manglende dokumentasjon gir `null` for prosent.

Navnet er et forslag til API-kontrakt. Invarianten er fast: **En prosent i dette grunnlaget er dokumentert; konsumenten skal ikke falle tilbake til råfeltet.** Fraværende nytt felt under utrulling betyr heller ikke at råfeltet kan brukes.

Historikk får tilsvarende kildevurdering per hendelse/snapshot. Backend beholder intern sporbarhet til registreringen, men trenger ikke eksponere nye personopplysninger i visningskontrakten.

Periodiseringen må ta hensyn til dokumentasjon, ikke bare verdi:

- Skjulte eller udokumenterte prosenter må ikke bli 100 % gjennom `FALLBACK_DELTAKELSESPROSENT`. Dagens periodemodell har denne fallbacken og en ikke-null prosent. Den kan derfor ikke brukes uendret som dokumentasjon. [K6], [K7]
- Ikke filtrer bort en nyere udokumentert registrering og gjenbruk en eldre dokumentert prosent som om den fortsatt gjelder.
- Like tall fra ulike kilder må ikke slås sammen slik at udokumenterte perioder arver dokumentasjon.
- Startdatoendringer, tilbakevirkende endringer og fremtidige endringer må beholde koblingen mellom verdi, kilde og periode.
- Den eksisterende regelen om direkte oppdatering fra startdato og timejobben for andre fremtidige endringer skal ikke endres utilsiktet.

Legacy-fallback kan bli stående i en avgrenset legacy-sti for konsumenter utenfor oppgaven. Ingen visning innenfor oppgaven skal bruke den. En global omskriving av delte modeller er ikke en forutsetning.

## Arbeidspakker og rekkefølge

| Steg | Arbeid | Ferdig når |
|---|---|---|
| 1. Lås datakrav og avhengigheter | Lag karakteriseringstester for dagens registrering, periodisering og delte kallere. Dokumenter hvilke eldre registreringsveier som faktisk beviser prosentvalg. Kartlegg JSON-/API-/hendelseskonsumenter før metadata legges til. | Hver kilde er klassifisert som dokumentert eller ukjent. Arrangørsporet har en eksplisitt kompatibilitetsgrense. |
| 2. Bevar dokumentasjon ved registrering | Fjern automatiske 100 %-valg i eksisterende skjemaer. Utvid requests, lagring og snapshots med dokumentasjon av eksplisitte valg. Ikke åpne felt for flere tiltakstyper. | Eksplisitt 100 % kan skilles fra manglende valg. Kladd og godkjenning mister ikke dokumentasjonen. |
| 3. Bygg visningsgrunnlag i amt-deltaker | Beregn kildevurdert nåværende/neste mengde og historikkgrunnlag. Ikke endre eksisterende råfelter, timejobb eller Kafka-felters betydning for arrangørsporet. | Dokumentert prosent er tilgjengelig uavhengig av tiltakstype. Ukjent prosent forblir null gjennom periodisering. |
| 4. Før grunnlaget gjennom amt-deltaker-bff | Utvid internkontrakt, `DeltakerModel`, Nav-/innbyggerresponser og historikkmapping. Nye felt innføres additivt. [K8] | BFF viderefører vurderingen fra amt-deltaker uten egen prosent-fallback. |
| 5. Endre frontendvisning | Oppdater formattering, detaljer, utkast og historikk til å bruke visningsgrunnlaget. Fjern tiltakstype- og enkeltplassfilter for dokumentert prosent. Bevar dager uten prosent. | Samme regler gjelder på alle relevante sider, uten nye registreringsmuligheter. |
| 6. Endre brevgrunnlag og PDF-er | Ta med dokumentert prosent i hendelsessnapshotet. Oppdater distribusjonsmapperne og eventuelle PDF-kontrakter/maler, også for enkeltplasser. | Brevet bruker opplysningene fra riktig hendelse, ikke dagens verdi. Manglende prosent gir aldri 100 %. |
| 7. Endre aktivitetskort | Bruk kildevurdert grunnlag fra amt-deltaker. Fjern tiltakstypefilteret for dokumentert prosent og tilpass endringsdeteksjon/oppfrisking. [K9] | Endret dokumentasjon kan oppdatere kortet selv om råverdien er uendret. Dager beholdes uten prosent. |
| 8. Rull ut og oppdater dokumentasjon | Følg utrullings- og kontrollpunktene nedenfor. Oppdater oversiktsdokumentet fra nåsituasjon til ny løsning når funksjonen er aktiv. | Akseptansematrisen er oppfylt, også for gamle data og blandede versjoner. |

Steg 1 er en beslutningsport før implementering. Det skal ikke innføres en generell historisk tilbakefylling uten dokumentert grunnlag. Steg 5–7 avhenger av at de får et kildevurdert grunnlag, ikke bare et nytt nullable prosentfelt med ukjent opprinnelse.

### Frontend og delt kode

`harDeltakelsesmengde` brukes i dag også til å styre registreringsfelt. Ikke utvid denne funksjonen globalt for å åpne visning for alle tiltak: det ville også kunne åpne nye registreringsmuligheter. Skill registreringsregelen fra visningsregelen. [K10]

Formattering, historikk og mengdekomponenter i `deltaker-flate-common` brukes på tvers av flater. Innfør en eksplisitt ny visningssti for komponentene innenfor oppgaven, eller en avgrenset visningspolicy. Arrangørens eksisterende kall og oppførsel skal ikke endres som bieffekt.

`HistorikkSoktInn` sender i dag `null` til prosentformatteringen, mens Arena-historikken sender den importerte råverdien. Begge må håndteres eksplisitt; fjerning av én fallback er ikke tilstrekkelig. [K11], [K12]

### Brev og aktivitetskort

Historiske PDF-er skal ikke regenereres. Ved ny behandling av en eldre hendelse uten dokumentasjon skjules prosenten, mens registrerte dager bevares. Dokumentasjonen skal følge hendelsens snapshot, slik at senere prosentendringer ikke endrer grunnlaget for et tidligere brev.

Enkeltplassmalen viser i dag bare dager. Skal en enkeltplass med dokumentert prosent kunne vises etter den nye regelen, må både DTO og mal støtte dette. Det krever ingen ny registreringsmulighet. [K13]

Aktivitetskort-publisheren henter allerede deltaker fra amt-deltaker ved Kafka-hendelser. Men publisering av kortet er betinget av endringsdeteksjon i lokale data. En ny kildevurdering alene kan derfor bli oversett hvis dedupliseringen fortsatt bare sammenligner gamle råfelter. Planlegg både endringsdeteksjon og kontrollert oppfrisking av eksisterende kort. Ikke forutsett at replay av en uendret melding oppdaterer kortet. [K9]

## Kompatibilitet og sikkerhetsgrenser

Dette er en endring i eksisterende tjenester. Eksisterende autentisering, eierskapskontroll og tilgangsregler videreføres. Ingen nye endpoints, nettverkstilganger, Nais-manifest eller CI/CD-workflows trengs som del av planen.

Nye interne felt og lagringsmetadata innføres additivt, med manglende felt tolket som ukjent. Gammel og ny produsent/konsument må prøves sammen. Ikke endre eksisterende Kafka-prosentfelters betydning eller globale domeneregler for å få den nye visningen til å fungere.

Arrangørsporet skal ikke måtte deployes for denne endringen. Hvis nye metadata i delte snapshots påvirker serialisering mot dette sporet, må de avgrenses i mappingen. Kompatibiliteten kan kontrolleres med eksisterende kontraktstester uten å endre arrangør-BFF.

🔴 Rød sone: Kildevurdering, tidsperioder og dokumentasjon av veilederhandlinger må forstås og vurderes særskilt før implementering.

## Test- og akseptansematrise

| Tilfelle | Forventning |
|---|---|
| AFT/VTA med dokumentert 80 % eller eksplisitt 100 % | Prosenten vises uendret. |
| En annen ordinær tiltakstype med dokumentert prosent | Prosenten vises, selv om tiltaket ikke får nytt registreringsfelt. |
| Enkeltplass med dokumentert prosent, med og uten dager | Prosent vises etter samme regel som andre tiltak. |
| Null, udokumentert lagret 100 % eller Arena-prosent | Ingen prosent. Registrerte dager vises fortsatt. |
| Kladd, utkast og avbrutt utkast med/uten dokumentasjon | Ingen automatisk 100 %, ingen tap av registrerte dager eller kildeinformasjon. |
| Innbygger godkjenner et eldre udokumentert utkast | Godkjenningen gjør ikke prosenten dokumentert. |
| Fremtidig endring, endring fra startdato og tilbakevirkende endring | Riktig verdi og dokumentasjon i riktig periode. |
| Nyere ukjent registrering etter en dokumentert verdi | Gammel prosent gjenoppstår ikke som gjeldende. |
| Historikk, vedtak og import | Hver hendelse vurderes ut fra sitt eget snapshot. |
| Nytt brev og behandling av eldre hendelse | Ingen fallback, riktig historisk grunnlag, ingen duplikatutsending. |
| Aktivitetskort med uendret råprosent, men endret dokumentasjon | Kortets synlige mengde oppdateres. |
| Gammel backend eller hendelse uten nye felt | Ny konsument skjuler udokumentert prosent, uten parsefeil eller fallback til råverdi. |
| Arrangørsporet | Ingen endret formattering, registrering eller krav om ny BFF-deploy. |

Bruk eksisterende Vitest-komponent-/enhetstester i frontend, Kotlin-tester for kildevurdering/periodisering, DB-integrasjonstester for metadata og eksisterende API-/hendelses-/PDF-tester for kontrakter. Kritiske reiser dekkes med eksisterende E2E-oppsett. Ingen nye testverktøy er planlagt.

Ved implementering kjøres målrettede tester underveis. Før PR kjøres `./gradlew check --no-daemon --no-configuration-cache` ved endringer i delte backendmoduler, i tråd med repoets instruksjoner. Frontendens eksisterende `test:no-watch`, `type-check` og `lint` brukes for berørte pakker og apper.

## Utrulling og oppfølging

1. Rull ut additive kontrakter og eventuell lagringsmigrasjon før nye requests og konsumenter tas i bruk. Nye felt skal kunne være fraværende.
2. Aktiver eksplisitte valg i eksisterende registreringsflyter. En eldre klient uten ny dokumentasjon skal ikke produsere falskt dokumenterte verdier.
3. Aktiver visningsgrunnlaget i dev og gå gjennom akseptansematrisen med syntetiske deltakere. Kontroller særlig eldre 100 %, import, enkeltplass og perioder.
4. Bruk eksisterende toggle-mønster for trinnvis aktivering i berørte flater og tjenester. Manglende støtte i et ledd skal ikke føre til at klienten bruker råverdi som erstatning.
5. Oppfrisk eksisterende aktivitetskort kontrollert. Ikke replay brevutsendinger som del av oppfriskingen.
6. Fjern midlertidig utrullingskode etter godkjent drift. Behold kompatibilitet som er nødvendig for arrangørsporet.

### Observerbarhet

Følg aggregerte tall for vist prosent, skjult prosent uten dokumentasjon og visning av bare dager, fordelt på faste visningsflater. Sammenlign gamle og nye resultater i dev og ved begrenset utrulling. Ikke bruk deltaker-ID, veileder-ID eller prosentverdi som metrikklabler, og ikke logg personopplysninger.

Eksisterende varsler for API-feil, hendelsesbehandling, PDF-feil og aktivitetskortfeil videreføres. Påvist visning av en prosent uten dokumentasjon er en funksjonell feil som skal stoppe videre aktivering. Forventet nedgang i antall viste prosenter for eldre data er ikke i seg selv en driftsfeil.

### Rollback

Ved feil stoppes videre aktivering og oppfrisking. Bruk visningsbryteren til å skjule prosent, men bevare dager og øvrig innhold. Ikke bruk automatisk 100 % eller udokumenterte råverdier som nød-fallback.

Additive metadata og registrerte valg beholdes ved rollback; ingen destruktiv datamigrasjon trengs. Tidligere publiserte aktivitetskort kan kreve en ny kontrollert oppfrisking. Allerede sendte brev kan ikke tilbakeføres ved en kode-rollback.

### Kontroll etter aktivering

- [ ] Dokumentert 100 % vises, mens udokumentert 100 % skjules.
- [ ] Dokumentert prosent vises uavhengig av tiltakstype og enkeltplass.
- [ ] Registrerte dager vises når prosent mangler.
- [ ] Ingen nye tiltakstyper har fått registrerings- eller endringsfelt.
- [ ] Utkast, historikk, brev og aktivitetskort følger samme kildekrav.
- [ ] Fremtidige og tilbakevirkende perioder har riktig dokumentasjon.
- [ ] Aktivitetskort oppdateres også ved endret dokumentasjon alene.
- [ ] Ingen gamle brev er sendt på nytt eller arkiverte PDF-er omskrevet.
- [ ] Arrangørsporet fungerer uendret uten ny BFF-deploy.
- [ ] Rollback skjuler prosent uten å gjeninnføre fallback.

## Kildegrunnlag

Backendstier er relative til `docs`. Frontendlenkene er GitHub-lenker låst til den analyserte revisjonen. Planen er utarbeidet mot frontend `af1d15a07867ebf76a2adabcde364d4c55d8dd07` og backend `b77cb7d93f51d35540daf85db88c930e7c932f73`.

[K1]: https://github.com/navikt/amt-deltaker-flate/blob/af1d15a07867ebf76a2adabcde364d4c55d8dd07/apps/nav-veileders-flate/src/model/PameldingFormValues.ts#L145-L173
[K2]: https://github.com/navikt/amt-deltaker-flate/blob/af1d15a07867ebf76a2adabcde364d4c55d8dd07/apps/nav-veileders-flate/src/utils/pamelding-form-utils.ts#L54-L88
[K3]: https://github.com/navikt/amt-deltaker-flate/blob/af1d15a07867ebf76a2adabcde364d4c55d8dd07/apps/nav-veileders-flate/src/components/tiltak/endre-deltakelse-modaler/endre-deltakelsesmengde/utils.ts#L25-L49
[K4]: ../amt-lib/models/src/main/kotlin/no/nav/amt/lib/models/deltaker/Vedtak.kt#L6-L18
[K5]: ../amt-lib/models/src/main/kotlin/no/nav/amt/lib/models/deltaker/DeltakerEndring.kt#L17-L25
[K6]: ../amt-lib/models/src/main/kotlin/no/nav/amt/lib/models/deltaker/deltakelsesmengde/Deltakelsesmengde.kt#L10-L50
[K7]: ../amt-felles/intern-api-kontrakter/src/main/kotlin/no/nav/amt/internapi/deltaker/response/DeltakelsesmengdeResponse.kt#L6-L20
[K8]: ../amt-deltaker-bff/src/main/kotlin/no/nav/amt/deltaker/bff/innbygger/api/InnbyggerDeltakerResponse.kt#L15-L60
[K9]: ../amt-aktivitetskort-publisher/src/main/kotlin/no/nav/amt/aktivitetskort/service/KafkaConsumerService.kt#L42-L87
[K10]: https://github.com/navikt/amt-deltaker-flate/blob/af1d15a07867ebf76a2adabcde364d4c55d8dd07/packages/deltaker-flate-common/utils/utils.ts#L164-L185
[K11]: https://github.com/navikt/amt-deltaker-flate/blob/af1d15a07867ebf76a2adabcde364d4c55d8dd07/packages/deltaker-flate-common/components/historikk/HistorikkSoktInn.tsx#L58-L64
[K12]: https://github.com/navikt/amt-deltaker-flate/blob/af1d15a07867ebf76a2adabcde364d4c55d8dd07/packages/deltaker-flate-common/components/historikk/HistorikkImportertFraArena.tsx#L50-L55
[K13]: ../amt-pdfgen/templates/sections/enkeltplass-deltakelsesmengde.hbs#L1-L6
