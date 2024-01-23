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

Det er noen ressurser som må opprettes manuelt i hvert GCP-prosjekt for å få Terraform til å fungere: 
1. ServiceAccount for Terraform med key (json) som har tilstrekkelige permissions for ressurser som skal opprettes
2. Bucket for Terraform-tilstand (`amt-terraform-state-dev` for dev)

Manuelt oppsett Github Actions: 
1. Legg til en repository secret som heter `GCP_SECRET_DEV` for dev og som inneholder json-keyen for service accounten for Terraform.

#### Manuelt oppsett database
Det må opprettes databasebruker for datastreamen, databasebrukeren må gis tilgang, og det må settes opp replikering. 
Dette er godt beskrevet her: https://github.com/navikt/nada-datastream?tab=readme-ov-file#forutsetninger-for-bruk. 
OBS! Ikke sett opp replikeringen før man nærmer seg klar til å lese inn dataene fra datastreamen, hvis ikke fyller disken seg opp! 