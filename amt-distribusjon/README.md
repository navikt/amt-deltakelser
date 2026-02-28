# amt-distribusjon

### Testcontainers
Tester starter opp Docker-containere via Testcontainers, noe som kan ta litt tid.  
Test-containere kan konfigureres til å **gjenbrukes** etter at testene er ferdige, noe som kan 
redusere oppstartstiden med opptil 90 % etter første kjøring.

For å aktivere dette må du opprette en `.testcontainers.properties`-fil i `$HOME` med innholdet:

```shell
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

I tillegg må miljøvariabelen `TESTCONTAINERS_REUSE=true` settes.

#### Obs

Det er viktig å være oppmerksom på at containerne **forblir kjørende** inntil de stoppes manuelt, for eksempel med:

```bash
docker stop {id}
```
eller
```bash
docker stop $(docker ps -q)
```

Hvis test-containere brukes i flere prosjekter samtidig, kan det oppstå risiko for gjenbruk på tvers av prosjekter.
Dette kan unngås ved å tildele hver container en unik label, for eksempel:

```kotlin
container.withLabel("reuse.UUID", "dc04f4eb-01b6-4e32-b878-f0663d583a52")
```

Merk at reuse ikke kan benyttes i CI/CD.