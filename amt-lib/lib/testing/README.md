# amt-lib.testing

## SingletonKafkaProvider og SingletonPostgresProvider

SingletonKafkaProvider og SingletonPostgresProvider starter opp en docker-container via TestContainers, dette kan ta forholdsvis lang tid og kan være plagsomt hvis man må gjøre dette hele tiden. Man kan konfigurere TestContainers til å ikke stoppe containere etter testene er kjørt, noe som gjør at oppstartstiden for testene reduseres med ca 90% etter første kjøring.

For å skru dette på må det opprettes en `.testcontainsers.properties` configfil i `$HOME` som inneholder `testcontainers.reuse.enable=true`:
```shell 
echo "testcontainers.reuse.enable=true" >> ~/.testcontainers.properties
```

En miljøvariabel `TESTCONTAINERS_REUSE=true` må også settes settes.

#### Obs
Det er viktig å være obs på at disse containerene kjørende blir kjørende på maskinen til de blir stoppet manuelt med `docker stop {id}` eller `docker stop $(docker -ps -q)` for å stoppe alle kjørende containerer.

Hvis man bruker reuse så vil disse providerne gjenbruke samme containere på tvers av apper, så hvis man kjører to sett med tester samtidig eller noe har gått galt med oppryddingen kan det skje feil. 

Hvis det virker som at noe har gått i lås prøv å stopp containerne.

Reuse kan naturligvis ikke benyttes i CI/CD.