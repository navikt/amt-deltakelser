# amt-distribusjon
amt-distribusjon har ansvar for journalføring av vedtak og å varsle tiltaksdeltaker og veileder om viktige endringer. 
NAV-veileder varsles gjennom OBO sitt filter i Modia Arbeidsrettet oppfølging. 
Deltaker varsles gjennom minside-varsel på navno. Ikke-digitale deltakere mottar varsel som brev.

## Utvikling
**Lint fix:** 
```
./gradlew ktlintFormat build
```
**Build:**
```
./gradlew build
```

### TestContainers
Tester starter opp Docker-containere via TestContainers, noe som kan ta lang tid.
Test-containere kan konfigureres til ikke å stoppe etter endte tester og kunne redusere 
oppstartstiden med inntil 90% etter første kjøring. 

For å skru på dette, må det opprettes en `.testcontainsers.properties` configfil i `$HOME` som 
inneholder `testcontainers.reuse.enable=true`:
```shell 
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

Miljøvariabelen `TESTCONTAINERS_REUSE=true` må settes.

#### Obs
Det er viktig å være oppmerksom på at containerne forblir kjørende inntil de blir stoppet 
manuelt med `docker stop {id}` eller `docker stop $(docker -ps -q)`.

Hvis test-containere benyttes i flere prosjekter samtidig, vil det være en risiko for gjenbruk 
på tvers av prosjekter. Dette kan forhindres ved å tildele hver container en unikt label: 
`container.withLabel("reuse.UUID", "dc04f4eb-01b6-4e32-b878-f0663d583a52")`.

Reuse kan naturligvis ikke benyttes i CI/CD.