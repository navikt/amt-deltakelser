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

*OBS: Husk å se over resultatet av `terraform plan` før du merger noe til main!*

### Manuelle steg

#### Manuelt oppsett Terraform
Bruk Terraform-versjonen som er angitt i [`.terraform-version`](.terraform-version), både lokalt og i CI.
Provider-låsefilene i `dev/` og `prod/` skal versjoneres.
CI bruker `terraform init -lockfile=readonly`, slik at provider-valget ikke endres under kjøring.

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
og kan hete f.eks. `amt-deltaker-datastream-credentials`.

## Feilsituasjoner

### Oppgradering av postgres versjon
Når man migrerer databaser til ny versjon kommer datastreamen til å stoppe opp, og kan ende opp som `Permanently Failed` i gcp consolet.

#### 1. Gjenopprett replication slot
Oppgraderingen fjerner logiske replication slots. Gjenopprett den med:
```sql
PERFORM PG_CREATE_LOGICAL_REPLICATION_SLOT('<app>_replication', 'pgoutput');
```

Du kan verifisere om det gikk bra ved å kjøre:
```sql
SELECT * FROM pg_replication_slots;
```

Bruk samme navn som er konfigurert i [datastreams.tf](./prod/datastreams.tf)

#### 2. Forsøk `Recover Stream`
Fyll inn navnet på replication slottet som ble gjennopprettet, og start recovery.

Hvis det gikk bra, gå til Backfill steget, hvis ikke så bør det være en beskrivelse av feilen i vinduet.

For eksempel kan det hende at datastreamen ikke har tilgang til tabeller som er nylig opprettet, da må man
grante privileges som vanlig med noe som:
```sql
DO
$$
    BEGIN
        IF EXISTS
            (SELECT 1 from pg_roles where rolname = 'datastream')
        THEN
            ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO "datastream";
            GRANT USAGE ON SCHEMA public TO "datastream";
            GRANT SELECT ON ALL TABLES IN SCHEMA public TO "datastream";
        END IF;
    END
$$;
END;
```

#### 3. Backfill
Siden datastreamen har vært nede en stund, kan det være at den har gått glipp av endringer i databasen.
Man kan da initiere en backfill av dataene, hvor datastreamen da leser inn all data fra tabellen på nytt.

1. Det kan gjøres ved å gå inn på streamen i consolet.
2. Finn fanen som heter objects
3. `Initiate Backfill` på en eller alle tabellene som skal etterfylles.

#### 4. Avstem data
Det kan være lurt å sjekke om vi har konsistente data i Big Query etter dette er gjort, gjør noen tellinger i BQ og postgres. 
Men vær obs på at BQ kan gjøre noe smart caching som fører til at det kan ta litt tid før man får det riktige resultatet.

Benytt f.eks. feltet modified_at for å sjekke om det er konsistente data.

**Eksempel for amt-distribusjon**

Spørring BigQuery:
```sql
SELECT MAX(modified_at) FROM amt_distribusjon_datastream.public_tiltakshendelse
```

Spørring PostgreSQL:
```sql
SELECT MAX(modified_at) FROM tiltakshendelse
```
Spørringene bør gi samme resultat.


### Oppgradering av kubernetes clustre, eller rotering av passord
Når nais oppgraderer kubernetes clustre kan det føre til at passord for databasebrukere blir rotert.
I det tilfellet må man opprette en ny versjon av secreten som inneholder brukernavn og passord for datastreamen sin postgresbruker.

Følg oppskriften som er gitt [her](#opprette-secret-for-datastream-bruker) for å hente ut oppdatert passord for brukeren.
