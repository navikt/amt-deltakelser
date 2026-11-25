# Deltakelsesprosent i amt-deltaker-flate og amt-deltakelser

## Kort fortalt

**Deltakelsesprosent vises først og fremst for arbeidsforberedende trening (AFT) og varig tilrettelagt arbeid i skjermet virksomhet (VTA).** I grensesnittet heter feltet vanligvis «Deltakelsesmengde». Det kan inneholde både prosent og antall dager per uke.

For enkeltplasser brukes også begrepet deltakelsesmengde, men flatene viser **bare dager per uke, ikke prosent**. En lagret prosentverdi er derfor ikke i seg selv nok til at prosent vises.

**`amt-deltaker` eier lagringen og endringslogikken.** `amt-deltaker-bff` henter deltaker og historikk derfra ved API-kall. Periodiseringsalgoritmen ligger i den delte modellen i `amt-lib/models`, og brukes av `amt-deltaker` når responsen bygges. [B1], [B2], [B3]

Kartleggingen gjelder lokal kildekode 23. november 2026: `amt-deltaker-flate` på `af1d15a0` og `amt-deltakelser` på `0a436be40`. Beskrivelsen gjelder implementasjonen i disse revisjonene, ikke observerte produksjonsdata.

## Når vises den i flatene?

| Flate og sted | Når vises deltakelsesprosent? | Hvor kommer visningsdataene fra? |
|---|---|---|
| Nav-veileders flate, påmeldingsskjema for ordinær plass | For AFT og VTA. Spørsmålet «Skal personen delta 100 % i tiltaket?» vises. Prosentfelt og valgfritt antall dager vises når veilederen velger «Nei». | Skjemaverdier initialisert fra deltakeren. Ved innsending blir «Ja» til `100`. [F1], [F2] |
| Nav-veileders flate, oppsummering av utkast | For AFT og VTA. For enkeltplass vises bare dager hvis de er oppgitt. | `deltaker.deltakelsesprosent` og `deltaker.dagerPerUke`. [F3] |
| Nav-veileders flate, deltakerdetaljer | For AFT og VTA. Ingen egen statusbegrensning rundt mengdekomponenten i `DeltakerInfo`. Regler for å vise eller endre selve deltakelsen kommer i tillegg. | `deltakelsesprosent`, `dagerPerUke` og `deltakelsesmengder.nesteDeltakelsesmengde` fra deltakerresponsen. [F4] |
| Nav-veileders flate, endring av deltakelsesmengde | Ordinære plasser får prosentfelt. Enkeltplasser får bare felt for dager per uke. Endringsvalget krever støttet tiltak/enkeltplass, tillatt status og at deltakelsen ikke er låst. [F26], [F32] | Normalt gjeldende deltakerdata, men ved behandling av arrangørforslag brukes verdiene i forslaget. [F5], [F6], [F27] |
| Innbyggers flate, utkast og avbrutt utkast | Samme felles visningsregel: prosent for AFT/VTA, bare dager for enkeltplass. | `deltakelsesprosent` og `dagerPerUke` fra innbyggerresponsen. Utkastsidene sender ikke inn neste periode til visningskomponenten. [F7], [F8] |
| Innbyggers flate, deltakerdetaljer | Samme felles visningsregel. En feilregistrert deltakelse får en egen informasjonsside uten denne komponenten. | Gjeldende felt og `nesteDeltakelsesmengde` fra innbyggerresponsen. [F7], [F9] |
| Arrangørflate, ordinære deltakerdetaljer | Bare AFT og VTA i skjermet virksomhet. Dette visningsvilkåret har ikke et eget statusfilter. | `deltaker.deltakelseProsent`, `dagerPerUke` og eventuell neste mengde fra arrangørresponsen. Merk annen skrivemåte på prosentfeltet. [F10], [F11] |
| Arrangørflate, begrenset visning av adressebeskyttet deltaker | Samme tiltakstyper som ordinære detaljer. Har ikke visning av neste periode. | Backend nullstiller prosent og dager når den ansatte ikke er veileder for deltakeren. Komponentens 100 %-fallback kan derfor bli vist i stedet for faktisk prosent. [F10], [F12], [B24] |
| Historikk og arrangørforslag | Vedtak og Arena-import bruker felles tiltakstype-/enkeltplassregel. Mengdeendringer og forslag viser prosent/dager fra selve hendelsen eller forslaget. | Historikkrespons eller forslag, ikke nødvendigvis dagens deltakerverdi. [F13], [F14] |
| Tiltakskoordinatorflate | Ingen direkte bruk av prosent, `dagerPerUke` eller deltakelsesmengde funnet i `apps/tiltakskoordinator-flate/src`. | Ingen egen visning identifisert i denne kartleggingen. |

### Felles regler som påvirker det brukeren ser

Felleskomponenten tillater deltakelsesmengde når tiltakskoden er `ARBEIDSFORBEREDENDE_TRENING`, `VARIG_TILRETTELAGT_ARBEID_SKJERMET` eller `erEnkeltplass` er sann. Andre tiltak skjuler komponenten selv om responsen inneholder prosent. [F15]

| Data og situasjon | Resultat i visningen |
|---|---|
| Ordinær plass, prosent `80` og dager `3` | `80 % fordelt på 3 dager i uka` |
| Ordinær plass, prosent `80` og dager `null` | `80 %` |
| Ordinær plass, prosent `null` | `100 %`, eventuelt med dager. Dette er en fallback i visningen, ikke bevis for at `100` er lagret. |
| Ordinær plass, prosent `0` | Formatteringen beholder `0 %`. Den bruker `?? 100`, ikke en regel som erstatter nullverdien `0`. |
| Enkeltplass med dager `3` | `3 dager i uka`. Prosenten ignoreres i teksten. |
| Enkeltplass uten dager, eller med dager `0` | Ingen mengdeseksjon når det heller ikke finnes en neste periode med visbar mengde. |
| Det finnes en neste periode | Nav og innbygger viser «Nåværende periode» og «Neste periode (fom. …)». Arrangør viser neste mengde og dato i parentes etter gjeldende mengde. |

Disse reglene ligger i [DeltakelsesmengdeInfo][F16] og formatteringen [deltakerprosentText][F17]. Arrangør har en egen formatterer med samme fallback fra `null` til `100`, men uten enkeltplassvisning. [F10]

## Hvordan henter frontend data?

Stiene nedenfor er API-stier. Nav- og innbyggerflatene bygger base-URL mot `amt-deltaker-bff`; eventuell inngangssti kommer foran. Arrangørflaten bruker `amt-tiltaksarrangor-bff`. [F18]

| Flate | Kall | Relevante responsfelt |
|---|---|---|
| Nav-veileder | `POST /deltaker/{deltakerId}` i `amt-deltaker-bff` | `deltakelsesprosent`, `dagerPerUke`, `deltakelsesmengder` |
| Innbygger | `GET /innbygger/{deltakerId}` i `amt-deltaker-bff` | `deltakelsesprosent`, `dagerPerUke`, `deltakelsesmengder` |
| Arrangør | `GET /tiltaksarrangor/deltaker/{deltakerId}` i `amt-tiltaksarrangor-bff` | `deltakelseProsent`, `dagerPerUke`, `deltakelsesmengder` |

API-kallene er definert i [Nav-klienten][F19], [innbyggerklienten][F20] og [arrangørklienten][F21]. Flatene formaterer verdiene, men beregner ikke deltakelsesprosent ut fra antall dager.

## amt-deltaker er kilden bak amt-deltaker-bff

```text
Påmelding, kladd eller godkjent endring fra Nav
  -> amt-deltaker-bff
  -> amt-deltaker
     -> deltaker: lagret prosent og dager per uke
     -> vedtak, import og endringshistorikk
        -> periodiseringslogikk i amt-lib/models
        -> neste og siste deltakelsesmengde
  -> amt-deltaker-bff
  -> Nav-veileders flate eller innbyggers flate
```

`AmtDeltakerClient.getDeltaker()` i BFF gjør `GET /deltaker/{deltakerId}` mot `amt-deltaker`. Historikk hentes også fra `amt-deltaker`. Endringer videresendes til `POST /deltaker/{deltakerId}/endre-deltaker`. Denne klienten cacher personident-oppslag, men ikke deltakerresponsen. [B1]

Det er derfor viktig å ikke stoppe analysen ved BFF-responsen. BFF har blant annet tilgangskontroll, inputvalidering og mapping, mens den autoritative lagringen og behandlingen av mengdeendringer ligger i `amt-deltaker`.

### Lagret nåverdi og beregnede perioder er forskjellige ting

| Opplysning | Hvor kommer den fra? | Betydning for visningen |
|---|---|---|
| `deltakelsesprosent` og `dagerPerUke` på deltakerresponsen | Lagrede felt på deltakeren. `DeltakerRepository` skriver `deltaker.deltakelsesprosent` og `dager_per_uke`; `DeltakerResponseBuilder` kopierer verdiene til responsen. [B2], [B4] | Dette er feltene flatene bruker som nåværende mengde. De beregnes ikke på nytt fra historikken ved hvert lesekall. |
| Periodisert mengdehistorikk | `historikk.toDeltakelsesmengder()`, med mengder fra vedtak, Arena-import og mengdeendringer. Endringer av startdato kan justere periodene. [B3], [B5] | Grunnlaget for hvilke perioder som er gyldige, og for neste planlagte mengde. |
| `gjeldende` i domenemodellen | Siste gyldige periode med `gyldigFra <= LocalDate.now()`. [B3] | Brukes blant annet når en utsatt endring skal materialiseres til deltakerens lagrede nåverdi. |
| `nesteDeltakelsesmengde` | Første periode etter `gjeldende`. Hvis ingen periode gjelder ennå, kan den første perioden være neste. [B3] | Vises som neste periode i detaljsidene. |
| `sisteDeltakelsesmengde` | Siste element i den beregnede periodelisten, også når dette ligger frem i tid. [B2] | Brukes blant annet som sammenligningsgrunnlag i endringsskjemaene. Betyr ikke nødvendigvis dagens mengde. |

`DeltakerResponseBuilder` avgrenser periodelisten mot deltakerens start- og sluttdato når startdato finnes. Algoritmen sorterer mengdene, fjerner ugyldiggjorte perioder og slår sammen påfølgende like mengder. [B2], [B3]

**Det finnes dermed to tidsmekanismer:** neste periode beregnes fra historikken ved lesing, mens nåverdien i deltakerfeltene oppdateres ved en endring eller en bakgrunnsjobb. De to må ikke omtales som samme beregning.

### Hvor kommer prosentdataene inn i amt-deltaker?

| Kilde | Behandling og lagring |
|---|---|
| Påmelding eller oppdatering av utkast | `PameldingService` setter prosent og dager på deltakeren fra utkastet. Opplysningene tas også med i vedtaksgrunnlaget. [B6] |
| Kladd | `KladdService` lager lagringsobjekt med prosent og dager fra requesten. [B7] |
| Nav endrer mengde | BFF sender endringen til `amt-deltaker`. `EndreDeltakelsesmengdeExtensions` avgjør om deltakerfeltene oppdateres straks eller senere. Endringen lagres separat i endringshistorikken. [B1], [B8], [B9] |
| Arrangørforslag | Forslaget kommer til `amt-deltaker` på arrangørmelding-topicet og lagres som forslag. En foreslått prosent er ikke dermed deltakerens vedtatte nåverdi. Når Nav behandler forslaget i endringsskjemaet, brukes den ordinære endringsflyten. [B10], [F27], [F28] |
| Arena-data for enkeltplass | `EnkeltplassDeltakerPayload.prosentDeltid` mappes til `deltakelsesprosent`. Consumeren lagrer deltakeren og et `ImportertFraArena`-snapshot, når toggle tillater lesing av Arena-data for tiltakstypen. [B11], [B12] |
| Tidligere Arena-importerte ordinære deltakere | Historikkmodellen støtter prosent fra `ImportertFraArena.deltakerVedImport`. Den opprinnelige import-/migreringsveien for ordinære AFT-/VTA-deltakere er ikke fastslått i denne kartleggingen. Den må skilles fra den identifiserte, aktive enkeltplassconsumeren. [B5] |

Prosent og dager er separate opplysninger. Det ligger ingen generell regel i denne flyten om at for eksempel tre dager skal regnes om til 60 %.

### Når blir en endring synlig som nåværende prosent?

1. `amt-deltaker` validerer at gyldig-fra-datoen ikke ligger før startdato eller etter sluttdato, når disse finnes. [B8]
2. Hvis endringen gjelder i dag eller tidligere, oppdateres deltakerens prosent og dager umiddelbart. **Unntak for fremtidig dato:** en endring som gjelder fra deltakerens startdato lagres også direkte som nåverdi, slik at mengden kan vises før oppstart. [B8]
3. Andre fremtidige endringer lagres med `behandlet = null`. Historikkberegningen kan da eksponere dem som neste periode, uten at dagens lagrede prosent er endret. [B2], [B9]
4. `DeltakelsesmengdeUpdateJob` kjører hver time, med fem minutters første forsinkelse etter oppstart, på lederen når applikasjonen er klar. Den henter ubehandlede mengdeendringer med nådd gyldig-fra-dato for deltakere i `VENTER_PA_OPPSTART` eller `DELTAR`. [B13], [B14]
5. Jobben oppdaterer nåverdien bare hvis endringen fremdeles tilsvarer historiens gjeldende mengde og verdiene er forskjellige. Endringen markeres behandlet. Ved vellykket endring lagres og publiseres den oppdaterte deltakeren. [B9], [B13]

En ordinær fremtidig mengdeendring blir altså **ikke nødvendigvis nåverdi ved midnatt**. Den er avhengig av jobbkjøringen og statusvilkårene. En tilbakevirkende endring eller endring fra startdato følger regelen om umiddelbar oppdatering i stedet.

### Null og 100 % finnes også i domenelogikken

Ved omforming av en mengdeendring til en historisk periode bruker domenemodellen `100F` hvis prosent mangler. Vedtak og Arena-import oppretter derimot bare en slik intern mengdeperiode når snapshotet har prosent. [B5]

Dette kommer i tillegg til frontendens egen fallback fra `null` til `100`. At brukeren ser 100 %, forteller derfor ikke alene om verdien kom fra input, lagret nåverdi, periodiseringslogikk eller formattering.

## Arrangørflaten har en annen lesesti

Arrangørflaten bruker ikke `amt-deltaker-bff` for deltakerdetaljene. `amt-tiltaksarrangor-bff` har en egen lagret deltakerkopi som fylles fra Kafka-topicet `amt.deltaker-v2`. Consumeren tar imot gruppedeltakere og lagrer oppdateringene i sin egen database. [B19], [B20]

```text
amt-deltaker: deltaker.deltakelsesprosent
  -> Kafka-payload: prosentStilling
  -> amt.deltaker-v2
  -> amt-tiltaksarrangor-bff: lagret deltakerkopi
  -> API-respons: deltakelseProsent
  -> arrangørflatens detaljvisning
```

`amt-deltaker` publiserer prosent fra deltakerens lagrede nåverdi. Arrangør-BFF mapper `prosentStilling` fra meldingen til lagringsmodellen og videre til `deltakelseProsent` i responsen. [B21], [B22], [B23]

Neste og siste mengde beregnes i arrangør-BFF fra historikken i kopien, med den samme delte periodiseringsmodellen, bare for tiltak med deltakelsesmengder. Dette er derfor en asynkron lesesti, i motsetning til Nav-/innbygger-BFFs direkte API-henting fra `amt-deltaker`. [B23]

Ved adressebeskyttelse og manglende veiledertilknytning nullstiller arrangør-BFF blant annet `deltakelseProsent` og `dagerPerUke`. Den begrensede frontendvisningen har likevel mengdeseksjonen for AFT/VTA og formatterer manglende prosent til 100 %. **100 % i denne visningen trenger derfor ikke å være den faktiske deltakelsesprosenten.** [B24], [F10], [F12]

## Hvor registreres og endres verdien?

| Handling | Hva frontend sender |
|---|---|
| Ordinær påmelding | Prosent og eventuelt dager fra skjemaet. «Ja» på 100 %-spørsmålet blir `100`; tiltak som ikke støtter mengde får ikke prosent fra denne request-byggeren. [F2] |
| Lagring av kladd | De aktuelle skjemafeltene `deltakelsesprosent` og `dagerPerUke` lagres. Kladdlagringen bruker selve prosentfeltet, mens innsendingen også tolker ja/nei-valget. [F22] |
| Nav endrer ordinær deltakelsesmengde | `POST /deltaker/{deltakerId}/deltakelsesmengde` med prosent, eventuelt dager, gyldig-fra-dato og begrunnelse. Ved behandling av forslag sendes også `forslagId`. Ved `100` sendes ikke dager fra denne modalen. [F5], [F28] |
| Nav registrerer eller endrer enkeltplass | Dager per uke, ikke et prosentfelt. Endringsmodalen setter `deltakelsesprosent: undefined`. [F6], [F23] |
| Arrangør foreslår ny mengde | `POST /tiltaksarrangor/deltaker/{deltakerId}/forslag/deltakelsesmengde` med prosent, eventuelt dager, gyldig-fra-dato og begrunnelse. Dette er et forslag, ikke en direkte oppdatering av gjeldende prosent i frontend. [F24] |

**Visning og endringsmulighet har ulike regler.** Arrangørens endringsmeny inkluderer også `TILRETTELAGT_ARBEID_ORDINAER` (VTAO), mens mengdefeltet i detaljvisningen bare tillater AFT og VTA i skjermet virksomhet. Dette er en forskjell i den aktuelle kildekoden, ikke en konklusjon om ønsket funksjonalitet. [F10], [F25]

## Andre steder opplysningene brukes

| Sted | Når og hvilket datagrunnlag? |
|---|---|
| Aktivitetskort | Mengdedetaljen bygges bare for tiltak med deltakelsesmengder. Den bruker prosent og dager på deltakerobjektet. Manglende/ugyldig prosent blir ikke automatisk til 100 % her. Hvis prosent mangler, men dager er gyldige, kan kortet vise bare dager. [B15] |
| Hovedvedtak, PDF-grunnlag | Mengdeteksten tas fra utkastet, ikke dagens deltakerverdi. I hovedvedtakmapperen kreves både støttet tiltakstype og at utkastet har prosent. Et hovedvedtak kan derfor utelate mengdeteksten samtidig som frontend viser 100 %-fallback. [B16] |
| Enkeltplass, PDF-grunnlag | Antall dager tas fra `utkast.dagerPerUke` til `deltakelsesmengdeAntallDager`. [B17] |
| Felles tekstbygging for brev | Formattereren har en egen 100 %-fallback ved manglende prosent. Hvilke brev som bruker den, og om den kalles, avgjøres av den enkelte mapperen. Den må ikke forveksles med hovedvedtakets eksplisitte null-sjekk. [B16], [B18] |

## Viktige skiller ved feilsøking og videre kartlegging

| Spørsmål | Hva man må skille mellom |
|---|---|
| Hvorfor vises 100 % når databasen mangler prosent? | Fellesformatteringen og arrangørformatteringen erstatter `null` med `100`. [F10], [F17] |
| Hvorfor vises ikke en prosent som finnes i responsen? | Tiltakstypen kan skjule mengdeseksjonen. Enkeltplass viser uansett bare dager. [F15], [F17] |
| Hvorfor viser historikken en annen prosent enn detaljsiden? | Vedtak, import og endringer beskriver tidligere opplysninger. Detaljsiden bruker deltakerresponsens gjeldende felt. [F4], [F29], [F30] |
| Hvorfor viser «Søkt inn» 100 % uten en egen prosent i historikkobjektet? | `HistorikkSoktInn` sender eksplisitt `null` som prosent til fellesformatteringen. For en støttet ordinær plass gir det 100 %-fallback. For enkeltplass vises bare dager. [F31], [F17] |
| Hvorfor vises en foreslått prosent uten at dagens prosent er endret? | Forslagsvisningen leser `forslag.endring`. Det er et annet datagrunnlag enn deltakerens gjeldende felt. [F14] |
| Hvorfor ser arrangør og Nav ulikt ut ved en planlagt endring? | Begge har støtte for neste periode, men presenterer den ulikt. Arrangørens side for adressebeskyttede viser ikke neste periode. [F9], [F11], [F12] |
| Hvorfor kan arrangør se eldre data enn Nav? | Nav-/innbygger-BFF henter fra `amt-deltaker` ved kall. Arrangør-BFF leser sin Kafka-oppdaterte kopi og er avhengig av at oppdateringen er konsumert. [B1], [B19], [B20] |
| Hvorfor kan neste periode være riktig mens dagens prosent ennå er gammel? | Neste periode beregnes fra historikk. Oppdatering av lagret nåverdi for utsatte endringer skjer via timejobben, med egne statusvilkår. [B2], [B9], [B13], [B14] |
| Hvorfor vises en ny prosent før startdato? | Endringer som gjelder fra deltakerens startdato oppdaterer nåverdien direkte, også når startdato ligger frem i tid. [B8] |

## Kildereferanser

Lenkene er relative til denne filen. Linjenumrene gjelder revisjonen angitt øverst.

[B1]: ../amt-deltaker-bff/src/main/kotlin/no/nav/amt/deltaker/bff/clients/AmtDeltakerClient.kt#L24-L85
[B2]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/api/response/DeltakerResponseBuilder.kt#L70-L104
[B3]: ../amt-lib/models/src/main/kotlin/no/nav/amt/lib/models/deltaker/deltakelsesmengde/Deltakelsesmengder.kt#L15-L105
[B4]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/repository/DeltakerRepository.kt#L183-L236
[B5]: ../amt-lib/models/src/main/kotlin/no/nav/amt/lib/models/deltaker/deltakelsesmengde/Deltakelsesmengde.kt#L16-L50
[B6]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/veileder/PameldingService.kt#L49-L82
[B7]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/veileder/KladdService.kt#L123-L143
[B8]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/veileder/endring/extensions/EndreDeltakelsesmengdeExtensions.kt#L16-L51
[B9]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/veileder/endring/DeltakerEndringService.kt#L79-L154
[B10]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/tiltaksarrangor/ArrangorMeldingConsumer.kt#L35-L69
[B11]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/kafka/payload/EnkeltplassDeltakerPayload.kt#L13-L38
[B12]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/enkeltplass/kafka/EnkeltplassDeltakerConsumer.kt#L52-L91
[B13]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/job/DeltakelsesmengdeUpdateJob.kt#L29-L65
[B14]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/veileder/endring/DeltakerEndringRepository.kt#L88-L105
[B15]: ../amt-aktivitetskort-publisher/src/main/kotlin/no/nav/amt/aktivitetskort/domain/Aktivitetskort.kt#L86-L129
[B16]: ../amt-distribusjon/src/main/kotlin/no/nav/amt/distribusjon/journalforing/pdf/PdfDtoMappere.kt#L29-L49
[B17]: ../amt-distribusjon/src/main/kotlin/no/nav/amt/distribusjon/journalforing/pdf/EnkeltplassPdfDtoMapper.kt#L59-L65
[B18]: ../amt-distribusjon/src/main/kotlin/no/nav/amt/distribusjon/journalforing/pdf/PdfDtoMappingUtils.kt#L358-L371
[B19]: ../amt-tiltaksarrangor-bff/src/main/kotlin/no/nav/tiltaksarrangor/consumer/KafkaConsumer.kt#L52-L88
[B20]: ../amt-tiltaksarrangor-bff/src/main/kotlin/no/nav/tiltaksarrangor/consumer/KafkaConsumerService.kt#L100-L136
[B21]: ../amt-deltaker/src/main/kotlin/no/nav/amt/deltaker/kafka/payload/DeltakerKafkaPayloadBuilder.kt#L183-L187
[B22]: ../amt-tiltaksarrangor-bff/src/main/kotlin/no/nav/tiltaksarrangor/consumer/model/DeltakerDto.kt#L28-L34
[B23]: ../amt-tiltaksarrangor-bff/src/main/kotlin/no/nav/tiltaksarrangor/service/DeltakerMapper.kt#L58-L157
[B24]: ../amt-tiltaksarrangor-bff/src/main/kotlin/no/nav/tiltaksarrangor/service/DeltakerMapper.kt#L163-L189
[F1]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/pamelding/standard/Deltakelsesprosent.tsx#L23-L86
[F2]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/utils/pamelding-form-utils.ts#L54-L88
[F3]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/utkast/UtkastDeltaker.tsx#L51-L60
[F4]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/tiltak/DeltakerInfo.tsx#L167-L178
[F5]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/tiltak/endre-deltakelse-modaler/endre-deltakelsesmengde/EndreDeltakelsesmengdeGruppeModal.tsx#L35-L125
[F6]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/tiltak/endre-deltakelse-modaler/endre-deltakelsesmengde/EndreDeltakelsesmengdeEnkeltplassModal.tsx#L99-L138
[F7]: ../../amt-deltaker-flate/apps/innbyggers-flate/src/guards/DeltakerGuard.tsx#L14-L38
[F8]: ../../amt-deltaker-flate/apps/innbyggers-flate/src/pages/UtkastPage.tsx#L132-L140
[F9]: ../../amt-deltaker-flate/apps/innbyggers-flate/src/pages/DeltakerPage.tsx#L191-L201
[F10]: ../../amt-deltaker-flate/apps/tiltaksarrangor-flate/src/utils/deltaker-utils.ts#L26-L40
[F11]: ../../amt-deltaker-flate/apps/tiltaksarrangor-flate/src/component/page/bruker-detaljer/deltaker-detaljer/DeltakelseInfo.tsx#L69-L135
[F12]: ../../amt-deltaker-flate/apps/tiltaksarrangor-flate/src/component/page/bruker-detaljer/DeltakerDetaljerAdresseBeskyttet.tsx#L69-L79
[F13]: ../../amt-deltaker-flate/packages/deltaker-flate-common/components/DeltakelsesmengdeHistorikkVisning.tsx#L13-L56
[F14]: ../../amt-deltaker-flate/packages/deltaker-flate-common/components/forslag/ForslagDetaljer.tsx#L132-L149
[F15]: ../../amt-deltaker-flate/packages/deltaker-flate-common/utils/utils.ts#L164-L185
[F16]: ../../amt-deltaker-flate/packages/deltaker-flate-common/components/DeltakelsesmengdeInfo.tsx#L18-L87
[F17]: ../../amt-deltaker-flate/packages/deltaker-flate-common/utils/displayText.ts#L26-L43
[F18]: ../../amt-deltaker-flate/packages/deltaker-flate-common/utils/api-url.ts#L1-L2
[F19]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/api/api.ts#L93-L127
[F20]: ../../amt-deltaker-flate/apps/innbyggers-flate/src/api/api.ts#L13-L33
[F21]: ../../amt-deltaker-flate/apps/tiltaksarrangor-flate/src/api/tiltak-api.ts#L107-L116
[F22]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/pamelding/standard/PameldingLagring.tsx#L40-L46
[F23]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/pamelding/enkeltplass/DeltakelsesmengdeValg.tsx#L6-L31
[F24]: ../../amt-deltaker-flate/apps/tiltaksarrangor-flate/src/api/forslag-api.ts#L64-L77
[F25]: ../../amt-deltaker-flate/apps/tiltaksarrangor-flate/src/component/page/bruker-detaljer/deltaker-detaljer/endringsknapper.ts#L51-L53
[F26]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/utils/endreDeltakelse.ts#L49-L51
[F27]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/components/tiltak/endre-deltakelse-modaler/endre-deltakelsesmengde/utils.ts#L25-L49
[F28]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/api/api.ts#L480-L503
[F29]: ../../amt-deltaker-flate/packages/deltaker-flate-common/components/historikk/HistorikkVedtak.tsx#L20-L70
[F30]: ../../amt-deltaker-flate/packages/deltaker-flate-common/components/historikk/HistorikkImportertFraArena.tsx#L50-L57
[F31]: ../../amt-deltaker-flate/packages/deltaker-flate-common/components/historikk/HistorikkSoktInn.tsx#L58-L64
[F32]: ../../amt-deltaker-flate/apps/nav-veileders-flate/src/utils/endreDeltakelse.ts#L210-L212
