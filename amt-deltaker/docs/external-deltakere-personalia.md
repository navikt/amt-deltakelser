# `/external/deltakere/personalia` API-dokumentasjon

## Formål
Dette endepunktet brukes av Mulighetsrommet for å hente personlig informasjon om deltakere til bruk i deres økonomiløsning.

## Autentisering
Krever client credentials token fra appen `mulighetsrommet-api`.

## Request
- **Metode:** POST
- **Sti:** `/external/deltakere/personalia`
- **Body:** JSON-array med UUID-er som representerer Deltaker-IDer.

### Eksempel på request
```json
[
  "a3f1c2d4-5678-4e9b-8a2b-123456789abc",
  "b2e4f6a8-1234-4cde-9f87-abcdef123456"
]
```

## Response
- **Body:** JSON-array med personalia-objekter for hver forespurt deltaker.

### Eksempel på respons
```json
[
  {
    "deltakerId": "a3f1c2d4-5678-4e9b-8a2b-123456789abc",
    "fornavn": "Ola",
    "mellomnavn": "Normann",
    "etternavn": "Nordmann",
    "personident": "12345678901",
    "navEnhetsnummer": "1234",
    "navEnhetsnavn": "NAV Test",
    "adressebeskyttelse": null,
    "erSkjermet": false
  },
  {
    "deltakerId": "b2e4f6a8-1234-4cde-9f87-abcdef123456",
    "fornavn": "Kari",
    "mellomnavn": null,
    "etternavn": "Nordmann",
    "personident": "10987654321",
    "navEnhetsnummer": "5678",
    "navEnhetsnavn": "NAV Test 2",
    "adressebeskyttelse": "STRENGT_FORTROLIG",
    "erSkjermet": true
  }
]
```

## Feilhåndtering
- Returnerer standard HTTP-feilkoder ved autentiseringsfeil, ugyldig input eller interne feil.
- Hvis en eller flere Deltaker-IDer ikke finnes, vil de bli ignorert i responsen.

## Se også
- [Kildekode for endepunktet](../src/main/kotlin/no/nav/amt/deltaker/external/api/SystemApi.kt)
- [Kildekode for response](../src/main/kotlin/no/nav/amt/deltaker/external/data/DeltakerPersonaliaResponse.kt)
