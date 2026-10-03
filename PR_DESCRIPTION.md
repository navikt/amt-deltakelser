## Beskrivelse

Standardiserer håndteringen av feil fra eksterne HTTP-kall i `amt-aktivitetskort-publisher` og `amt-tiltaksarrangor-bff` ved å bruke `executeUpstreamCall` fra `amt-lib/spring-boot`. Felles exception-typer klassifiserer midlertidige feil for retry og gir kontekst om tjeneste, operasjon og eventuell HTTP-status uten å ta med rå feilmeldinger fra upstream.

Klientene er tilpasset de nye exception-typene. Eksisterende særbehandling av 404 og 401/403 er beholdt, og BFF-ens exception-handler mapper retrybare upstream-feil til 503 og øvrige upstream-feil til 502. TokenX-tokenutvekslingen beholder sin `OAuth2AuthorizationException`-kontrakt.

Testene dekker klientenes feilbehandling, mappingen i BFF-ens exception-handler og retry-klassifisering for 408 og 429.

## Trello-kort

Ikke oppgitt.

## Type endring

- [ ] Feilretting
- [ ] Ny funksjonalitet
- [ ] Dokumentasjonsendring
- [x] Teknisk forbedring
- [ ] Annet

## Sjekkliste

- [x] Jeg har gått gjennom endringene.
- [x] Jeg har lagt til tester for endringene.
- [ ] Jeg har kommentert kode der det er nødvendig for å forstå den.
- [ ] Jeg har lenket pull request-en til et Trello-kort.
- [ ] Jeg har deployet og testet endringene manuelt.

## Manuelle tester

Testene er ikke kjørt. Gradle fikk ikke koblet til daemonen i cplt-sandboxen. Det må konfigureres `sandbox.allow_localhost_any` før testene kan kjøres der.
