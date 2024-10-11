# amt-bigquery-terraform

Dette repoet er en tilpasset kopi av [Flex-bigquery-terraform](https://github.com/navikt/flex-bigquery-terraform). 

Repoet inneholder Terraform-konfigurasjon for å flytte data fra applikasjonsspesifikke databaser i Google Cloud SQL til Google BigQuery 
med Google Datastream for Komet. Bakgrunnen for at data flyttes til BigQuery er et ønske om å kunne bruke BigQuery som 
datakilde for analyse og visualisering. 

Datastreams er valgt på grunn av at data blir oppdatert så fort de blir skrevet til eller endret i kildedatabasen. Alternativet er
Federated Queries, som typisk flytter data med angitte intervaller, basert på en SQL-spørring. Mange av våre databasetabeller har 
kolonner for `modifiedAt`, men ikke alle. For å være sikre på at vi får med alt som er relevant måtte vi ha flyttet alle data
fra enkelte tabeller hver gang, og det blir unødvendig tungvint. 

Begrunnelsen for å bruke Terraform i stedet for å opprettet ressursene direkte i Cloud Console er todelt. Først og fremst 
gir det teamet en deterministisk måte å opprette og slette ressurser på. For det andre fungerer konfigurasjonen fungerer som 
dokumentasjon på hvilke ressurser som er opprettet. 

### Manuelle steg

#### Manuelt oppsett Terraform
Det er noen ressurser som må opprettes manuelt i hvert GCP-prosjekt for å få Terraform til å fungere: 
- ServiceAccount for Terraform
  - Gi tilstrekkelige permissions for ressurser som skal opprettes
  - Opprett key i json-format, og ta vare på filen som lastes ned
- Bucket for Terraform-tilstand (`amt-terraform-state-dev` for dev)

Manuelt oppsett GitHub Actions: 
- Legg til en repository secret som heter `GCP_SECRET_DEV` for dev og som inneholder json-keyen for service accounten for Terraform. 

Oppsett fra egen maskin: 
- Kjør `export GOOGLE_CREDENTIALS=~/<key>.json` (`<key>.json` er filen som inneholder json-keyen for service accounten for Terraform).

#### Manuelt oppsett database
Det må opprettes databasebruker for datastreamen, databasebrukeren må gis tilgang, og det må settes opp replikering. 
Dette er godt beskrevet her: https://github.com/navikt/nada-datastream?tab=readme-ov-file#forutsetninger-for-bruk. 
OBS! Ikke sett opp replikeringen før man nærmer seg klar til å lese inn dataene fra datastreamen, hvis ikke fyller disken seg opp!

#### Opprette secret for datastream-bruker
Det må opprettes en secret i Google Secret Manager som inneholder brukernavn og passord for databasebrukeren for datastreamen. 
Dette gjør du manuelt i GCP-konsollet. Brukernavn og passord finner du i kubernetes-secreten som ble opprettet av nais da 
databasebrukeren ble opprettet, og du kan hente ut verdiene slik: 
`kubectl get secret <secret-navn> -o json | jq ".data | map_values(@base64d)"` (installer `jq` med brew hvis du ikke har det installert). 
Secreten skal være på formatet 

```
{
"username": "<brukernavn>",
"password": "<passord>"
}
```
og kan hete f.eks. `amttiltak-datastream-credentials`. 