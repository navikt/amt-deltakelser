# amt-aktivitetskort-publisher

Leser inn data fra amt-domenet og publiserer til AKaaS for å generere og oppdatere aktivitetskort

## Oppfølgingsperioder

Appen henter gjeldende oppfølgingsperiode fra veilarboppfolging og lagrer periode-ID-en på `melding`.
Fra Flyway-migrering V28 skriver appen ikke lenger til `oppfolgingsperiode`, og periode-ID-en har ingen fremmednøkkel til tabellen.
Tabellen beholdes for rollback til forrige appversjon. Den kan slettes i en senere release når rollback-vinduet er lukket og eventuell ekstern bruk er avklart.

Codestyle fix: `./gradlew ktlintFormat build -p amt-aktivitetskort-publisher`
Build: `./gradlew build -p amt-aktivitetskort-publisher`
