# amt-pdfgen

## Kom i gang
Kjør lokalt med:
```shell
docker-compose build && docker-compose up
```

ev hvis docker-compose build feiler:
```shell
docker build -t amt-pdfgen .
docker-compose up
```

## Endepunkter 
Alle malene under `templates/amt` svarer til et endepunkt som kan brukes enten til testing eller av andre applikasjoner.
Gå til http://localhost:8080/api/v1/genpdf/amt/hovedvedtak for å se et eksempel på en pdf. 

## Maler
Prosjektet har en standard mal som benyttes på de fleste tiltakstyper.
`/hovedvedtak` endepunktet er det som kalles fra `amt-distribusjon` og sørger for at man får riktig mal utifra tiltakstype. For de tiltakene som følger standard mal brukes `hovedvedtak-standard` og tanken er at man skal opprette nye filer for tiltakstyper som trenger andre tilpasninger(sånn som `hovedvedtak-vta`og `hovedvedtak-kurs`).

## Sections
`/sections` mappa inneholder seksjoner som gjenbrukes på tvers av hovedvedtak-maler. Her er det noen seksjoner som kun brukes på vta og digoppf men ikke på standard mal. Seksjoner som kun brukes i en hovedvedtak-mal er ikke flyttet ut i andre filer sånn at det skal være tydelig hva som er spesifikt for malen.

## Testing
Hvert template i `templates/amt/*` skal ha en json fil med testdata i `data/amt/`
Endringer i templates eller data blir visende i pdfen etter en refresh.
- http://localhost:8080/api/v1/genpdf/amt/hovedvedtak
- http://localhost:8080/api/v1/genpdf/amt/hovedvedtak-vta
- http://localhost:8080/api/v1/genpdf/amt/hovedvedtak-kurs

## Handlebars
https://github.com/navikt/pdfgen-core/blob/main/src/main/kotlin/no/nav/pdfgen/core/template/Helpers.kt