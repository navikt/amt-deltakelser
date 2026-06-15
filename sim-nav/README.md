# sim-nav

Denne applikasjonen simulerer amt-applikasjonenes avhengigheter, både interne (f.eks. Valp/mulighetsrommet)
og eksterne (f.eks. Altinn).

Dette gjør det mulig å kjøre opp amt-applikasjonene på lokal maskin, uten å måtte bygge eller konfigurere
applikasjoner fra andre team eller leverandører.

## Mål

* Gjøre det enkelt å kjøre opp "vår kode" lokalt
* Gi raskere feedbackloop uten venting på deploy, særlig ved fullstackutvikling
* Minimere behov for `if (isMock)` og lignende i kode som kan treffe produksjon
* Tillate redigering og lokal persistering av testdata der det er hensiktsmessig
* Forenkle reproduksjon av feilsituasjoner som krever bestemte tilstander i eksterne systemer

## Ikke-mål

Det er *ikke* meningen at denne applikasjonen skal

* Simulere eksterne systemers forretningslogikk
* Kunne rulles ut i NAVs testmiljøer
* Kjøre i Docker/Kubernetes
* Være nyttig for andre team