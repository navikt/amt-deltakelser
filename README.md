# amt-biqquery-terraform

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