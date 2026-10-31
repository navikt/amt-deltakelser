# amt-bigquery-terraform

Dette repoet er en tilpasset kopi av [Flex-bigquery-terraform](https://github.com/navikt/flex-bigquery-terraform). 

Repoet inneholder Terraform-konfigurasjon for å flytte data fra applikasjonsspesifikke databaser i Google Cloud SQL til Google BigQuery 
med Google Datastream for Komet. Bakgrunnen for at data flyttes til BigQuery er et ønske om å kunne bruke BigQuery som 
datakilde for analyse og visualisering. 

Datastream er valgt fordi data blir oppdatert så fort de blir skrevet til eller endret i kildedatabasen. Alternativet er
Federated Queries, som typisk flytter data med angitte intervaller, basert på en SQL-spørring. Mange av våre databasetabeller har 
kolonner for `modifiedAt`, men ikke alle. For å være sikre på at vi får med alt som er relevant måtte vi ha flyttet alle data
fra enkelte tabeller hver gang, og det blir unødvendig tungvint. 

Begrunnelsen for å bruke Terraform i stedet for å opprette ressursene direkte i Cloud Console er todelt. Først og fremst
gir det teamet en deterministisk måte å opprette og slette ressurser på. For det andre fungerer konfigurasjonen som
dokumentasjon på hvilke ressurser som er opprettet. 

**En merge til `main` starter automatisk apply i både dev og prod. Se gjennom planene før du merger.**

## Miljøer

| Mappe | GCP-prosjekt | Bucket for Terraform-state |
|-------|--------------|----------------------------|
| [`dev/`](dev/) | `amt-dev-d98f` | `amt-terraform-state-dev` |
| [`prod/`](prod/) | `amt-prod-d4dd` | `amt-terraform-state-prod` |

Prod har Datastream-oppsett og BigQuery-views for `amt-deltaker` og `amt-distribusjon`.
Dev er ikke i aktiv bruk, og Datastream for `amt-deltaker` er deaktivert som standard.
Dev har likevel fellesressurser for Datastream-nettverket som Terraform fortsatt forvalter.

## Lokal arbeidsflyt

Kjør kommandoene fra `amt-bigquery-terraform/` (fra repo-roten: `cd amt-bigquery-terraform`).
Du trenger Git, Nais CLI, Google Cloud CLI (`gcloud`) og 
Terraform-versjonen i [`.terraform-version`](.terraform-version).
Kontoen din må ha tilgang til ressursene og state-bucketen i miljøet du jobber med.

### Logg inn

Hvis du tidligere satte `GOOGLE_CREDENTIALS` for å bruke en service account-nøkkel,
fjern overstyringen fra terminalen før du logger inn:

```bash
unset GOOGLE_CREDENTIALS
```

Fjern også eventuell eksport av variabelen fra shell-oppsettet ditt, slik at den ikke kommer tilbake i nye terminaler.

Logg inn med Nais CLI før du kjører Terraform lokalt:

```bash
nais login
```

Miljøvariablene `GOOGLE_CREDENTIALS` og `GOOGLE_APPLICATION_CREDENTIALS` kan styre hvilke credentials Terraform bruker.
Du trenger ikke laste ned en service account-nøkkel for denne arbeidsflyten.

### Initialiser og lag planer

Initialiser begge miljøene uten å endre provider-låsefilene:

```bash
terraform -chdir=dev init -lockfile=readonly &&
terraform -chdir=prod init -lockfile=readonly
```

Kontroller formatering og konfigurasjon før du lager planer:

```bash
terraform fmt -check -recursive &&
terraform -chdir=dev validate &&
terraform -chdir=prod validate &&
terraform -chdir=dev plan &&
terraform -chdir=prod plan
```

Se gjennom begge planene, også når du endrer en delt modul.
Avklar uventede slettinger og erstatninger før du går videre.
`No changes` betyr at Terraform ikke finner avvik, ikke at alle view-spørringer eller Metabase-tilkoblinger fungerer.

Provider-låsefilene `dev/.terraform.lock.hcl` og `prod/.terraform.lock.hcl` skal være med i Git.
Ikke bruk `terraform init -upgrade` i den vanlige initialiseringen.
Oppgrader providerne i en egen endring, og se gjennom låsefilene og nye planer.
Når du oppgraderer Terraform, må versjonen være den samme i `.terraform-version`,
`dev/main.tf`, `prod/main.tf` og workflowen.

### Manuell apply

Vanlig deploy skjer gjennom CI etter merge. Kjør bare manuell apply når det er avtalt,
for eksempel ved kontrollert synkronisering av Terraform og GCP.
En apply kan endre eller slette ressurser. Kontroller riktig miljø og hele planen før du godkjenner.

Eksempel for prod:

```bash
terraform -chdir=prod apply
```

Kommandoen lager en ny plan og ber om godkjenning. Den bruker ikke planen fra en tidligere `terraform plan`.
For en avtalt endring i dev bruker du `-chdir=dev` i stedet.
Ikke legg state, planfiler eller credentials i Git.

## Endringer og CI

Jobb på en branch med prefikset `komet-bau/`, push den og opprett en pull request.
[Terraform-workflowen](.github/workflows/terraform.yml) kjører ved hver push:

| Branch | Plan | Automatisk apply |
|--------|------|------------------|
| `main` | Dev og prod | Dev og prod |
| `dev-*` | Dev og prod | Dev |
| Andre brancher, inkludert `komet-bau/*` | Dev og prod | Nei |

CI kontrollerer formatering i hele repoet og validerer begge miljøene.
Hver apply-jobb venter på at plan-jobben for samme miljø lykkes.
Apply-jobbene kjører med `-auto-approve`. De lager nye planer og bruker ikke lagrede planer fra plan-jobbene.

CI bruker de eksisterende repository-secretene `GCP_SECRET_DEV` og `GCP_SECRET_PROD` for GCP-tilgang.
CI-tilgangen er uavhengig av lokal innlogging med `nais login`.
GitHub-tokenet har kun `contents: read`. GCP-rettighetene kommer fra GCP-credentials.
Ikke skriv secret-verdier i logger, pull requests eller dokumentasjon.

## Metabase-tilgang

Metabase-tilgangene for de publiserte viewene er definert i
[`prod/metabase-access.tf`](prod/metabase-access.tf).
Hvert dataprodukt har sin egen Metabase-service account. Finn riktig konto i
Datamarkedsplassen før du legger til eller endrer en kobling mellom view og konto.
Ikke gjenbruk kontoen fra et annet dataprodukt uten å kontrollere at den er riktig.

Terraform gir `roles/bigquery.dataViewer` på hvert view i disse koblingene og
`roles/bigquery.metadataViewer` på datasettet som inneholder viewet.
Metadata-rollen gjør metadata om alle objekter i datasettet synlige, men gir ikke lesetilgang til rådataene.
Datasettilgangene ligger i de eksisterende `access_roles`-listene i
[`prod/datastreams.tf`](prod/datastreams.tf).
Bevar `authorized_views` og øvrige tilganger når du endrer disse listene.
Ikke legg til parallelle dataset-IAM-ressurser som konkurrerer om de samme tilgangene.

Etter endringer i tilganger eller view-skjema må du synkronisere skjemaet til
dataproduktets tilkobling i Metabase. Velg **Admin → Databases → Sync database schema**.
Kontroller også at spørringene og de berørte grafene fungerer.
Skjemasynkronisering er ikke det samme som å skanne feltverdier.

## Manuelle steg ved førstegangsoppsett

### Terraform og CI

Før første init må GCP-prosjektet ha en state-bucket, og CI må ha en service account
med rettigheter til ressursene Terraform skal forvalte.
Bucket-navnene står i miljøtabellen over.
CI-credentials ligger i repository-secretene som er beskrevet under [Endringer og CI](#endringer-og-ci).
Bruk `nais login` fra egen maskin, ikke en kopi av CI-nøkkelen.

### Manuelt oppsett av database

Opprett en databasebruker for datastreamen, gi databasebrukeren tilgang og sett opp replikering.
Se [forutsetningene for bruk av nada-datastream](https://github.com/navikt/nada-datastream?tab=readme-ov-file#forutsetninger-for-bruk).
Vent med å sette opp replikeringen til du er klar til å lese inn dataene fra datastreamen. Ellers fylles disken opp.

### Opprette secret for Datastream-bruker

Opprett en secret i Google Secret Manager med brukernavn og passord for databasebrukeren for datastreamen.
Dette gjør du manuelt i GCP-konsollen. Brukernavn og passord finner du i Kubernetes-secreten som Nais opprettet da
databasebrukeren ble opprettet. Du kan hente ut verdiene slik:
`kubectl get secret <secret-navn> -o json | jq ".data | map_values(@base64d)"` (installer `jq` med brew hvis du ikke har det installert). 
Secreten skal ha følgende JSON-format:

```
{
"username": "<brukernavn>",
"password": "<passord>"
}
```
og kan hete f.eks. `amt-deltaker-datastream-credentials`.

## Feilsituasjoner

### Oppgradering av PostgreSQL-versjon

Når du migrerer en database til en ny versjon, stopper datastreamen.
Den kan ende i tilstanden `Permanently Failed` i GCP-konsollen.

#### 1. Gjenopprett replication slot
Oppgraderingen fjerner logiske replication slots. Gjenopprett den med:
```sql
SELECT PG_CREATE_LOGICAL_REPLICATION_SLOT('ds_replication', 'pgoutput');
```

Du kan verifisere om det gikk bra ved å kjøre:
```sql
SELECT * FROM pg_replication_slots;
```

Bruk samme navn som er konfigurert i [datastreams.tf](./prod/datastreams.tf).

#### 2. Forsøk `Recover Stream`
Fyll inn navnet du brukte da du gjenopprettet replication slot, og start recovery.

Hvis recovery lykkes, gå til backfill-steget. Hvis ikke, se etter en beskrivelse av feilen i vinduet.

For eksempel kan datastreamen mangle tilgang til nylig opprettede tabeller. Da må du
gi tilgang, for eksempel med:
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
Du kan da starte en backfill, der datastreamen leser inn alle data fra tabellen på nytt.

1. Åpne streamen i GCP-konsollen.
2. Velg fanen `objects`.
3. Velg `Initiate Backfill` for én eller alle tabellene som skal etterfylles.

#### 4. Avstem data
Kontroller at dataene i BigQuery og PostgreSQL er konsistente etterpå, for eksempel ved å sammenligne antall rader.
Vær oppmerksom på at caching i BigQuery kan gjøre at det tar litt tid før du får riktig resultat.

Bruk for eksempel feltet `modified_at` for å kontrollere at dataene er konsistente.

**Eksempel for amt-distribusjon**

Spørring i BigQuery:
```sql
SELECT MAX(modified_at) FROM amt_distribusjon_datastream.public_tiltakshendelse
```

Spørring i PostgreSQL:
```sql
SELECT MAX(modified_at) FROM tiltakshendelse
```
Spørringene bør gi samme resultat.


### Oppgradering av Kubernetes-clustere eller rotering av passord

Når Nais oppgraderer Kubernetes-clustere, kan det føre til at passord for databasebrukere blir rotert.
Da må du opprette en ny versjon av secreten med brukernavn og passord for Datastream-brukeren i PostgreSQL.

Følg [oppskriften for Datastream-brukerens secret](#opprette-secret-for-datastream-bruker) for å hente ut oppdatert passord for brukeren.
