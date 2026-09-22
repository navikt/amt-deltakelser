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