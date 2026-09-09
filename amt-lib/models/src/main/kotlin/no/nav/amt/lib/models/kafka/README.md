## Pakkens innhold
Denne pakken inneholder kafka kontrakter som brukes for å sende og motta meldinger mellom ulike tjenester i teamets systemer og eksterne teams systemer. 
Kontraktene definerer strukturen på meldingene, slik at alle tjenester kan forstå og prosessere dem på en konsistent måte.

Endringer på datamodeller her må gjøres med forsiktighet, da det påvirker flere tjenester som bruker disse kontraktene.

## Forsiktighetsregler
- Skal data relastes for at alle konsumenter får nye data?
- Om felter fjernes, hvilke konsumenter som bruker disse dataene? Det kan også være apper utenfor monorepoet
- Siden konsumentene og produsentene bruker samme datamodeller så er det utfordrende å fjerne felter uten at konsumentene begynner å feile. Derfor bør man fjerne bruk av dataene fra konsumentene først 