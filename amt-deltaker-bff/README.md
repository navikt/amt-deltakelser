# amt-deltaker-bff
Backend for frontend som brukes for: 
* Nav veileder (i modia)
* Nav tiltaksansvarlig (i tiltaksadministrasjon)
* Innbygger (via aktivitetsplanen)

## ansvarsområde
* Tilgangsstyring og validering av innkommende forespørsler
* Self service tilgangsstyring for tiltaksansvarlige
* Delegerer handlinger som skal utføres på deltaker til amt-deltaker
* Sporbarhetslogger

* Spor etter andre ting enn dette kan dukke opp men er under utfasing i prosjekt for å forenkle applikasjonen

### Kjør lokalt

Start Kafka og database:
```shell
docker-compose up -d
```

Sett opp runtime configuration med følgende miljøvariabler og kjør med IntelliJ:
```shell
DB_USERNAME=myuser
DB_PASSWORD=mypassword
DB_DATABASE=mydb
DB_HOST=localhost
DB_PORT=5432
AZURE_OPENID_CONFIG_JWKS_URI="http://foo.bar"
```

eller kjør:
```shell
export DB_USERNAME=myuser && 
export DB_PASSWORD=mypassword && 
export DB_DATABASE=mydb && 
export DB_HOST=localhost && 
export DB_PORT=5432 && 
export AZURE_OPENID_CONFIG_JWKS_URI="http://foo.bar"
./gradlew run
```

# Databasetabeller
- nav_ansatt brukes for tilgangsstyring(innlogget brukers navIdent må omformes til nav id som brukes i tabellen for tilgangsstyring). Her bør det vurderes å bruke nav ident i tabellen isteden sånn at vi ikke må hente id
- tiltakskoordinator_deltakerliste_tilgang - self service tilgangsstyring på deltakerliste som også kan endres basert på deltakerliste info(tilgang stenges ved endringer på deltakerliste)
## deltakerliste tabell
- deltakerliste tabell brukes kun til å vite om den deltakerliste er "stengt" og for referanser fra andre tabeller

## Vurderes utfaset
- arrangor, brukes for å få tak i org nummer som brukes i modell
- tiltakstype, bør vurderes slettes og evt putte tiltakskode flatt i detlakerliste tabellen
 
## Under utfasing
- deltaker, trengs ikke lengre siden denne hentes fra amt-deltaker
- deltaker_status, fjernes når deltaker tabellen fjernes
- nav_bruker (innbygger), brukes kun for deltaker som skal slettes
- forslag hentes fra amt-deltaker
- nav_enhet - brukes ikke 
- ulest_hendelse - hentes fra amt-deltaker
- vurdering - hentes fra amt-deltaker