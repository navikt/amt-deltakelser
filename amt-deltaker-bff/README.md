# amt-deltaker-bff
BFF for tiltakssiden for deltaker og veileder. 

## Utvikling
**Lint fix:** 
```
./gradlew ktlintFormat build
```
**Build:**
```
./gradlew build
```

### Kjør lokalt

Start kafka og database:
```shell
docker-compose up -d
```

Sett opp runtime configuration med disse miljøvariablene og kjør via intellij:
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

