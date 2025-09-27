module "amttiltak_deltaker_personident_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "amttiltak_deltaker_personident_view"

  view_schema = jsonencode([
    { mode = "NULLABLE", name = "id", type = "STRING", description = "Unik ID for deltaker." },
    { mode = "NULLABLE", name = "bruker_id", type = "STRING", description = "Amt-ID til personen som deltar." },
    { mode = "NULLABLE", name = "gjennomforing_id", type = "STRING", description = "ID til gjennomføring (fra Valp)." },
    { mode = "NULLABLE", name = "start_dato", type = "DATE", description = "Startdato for deltakelse." },
    { mode = "NULLABLE", name = "slutt_dato", type = "DATE", description = "Sluttdato for deltakelse." },
    { mode = "NULLABLE", name = "dager_per_uke", type = "FLOAT", description = "Antall dager pr uke deltaker deltar." },
    { mode = "NULLABLE", name = "prosent_stilling", type = "FLOAT", description = "Deltakelsesprosent." },
    { mode = "NULLABLE", name = "registrert_dato", type = "TIMESTAMP", description = "Dato deltakeren ble påmeldt." },
    { mode = "NULLABLE", name = "kilde", type = "STRING", description = "Hvor deltakeren opprinnelig ble opprettet - ARENA eller KOMET." },
    { mode = "NULLABLE", name = "person_ident", type = "STRING", description = "Fødselsnummer til personen som deltar." }
  ])

  view_query = <<EOF
SELECT
  deltaker.id,
  deltaker.bruker_id,
  deltaker.gjennomforing_id,
  deltaker.start_dato,
  deltaker.slutt_dato,
  deltaker.dager_per_uke,
  deltaker.prosent_stilling,
  deltaker.registrert_dato,
  deltaker.kilde,
  bruker.person_ident
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker` deltaker
INNER JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_bruker` bruker
ON deltaker.bruker_id = bruker.id
EOF
}

module "amttiltak_deltaker_siste_status_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "amttiltak_deltaker_siste_status_view"

  view_schema = jsonencode([
    { mode = "NULLABLE", name = "id", type = "STRING", description = "Unik ID for deltaker." },
    { mode = "NULLABLE", name = "bruker_id", type = "STRING", description = "Amt-ID til personen som deltar." },
    { mode = "NULLABLE", name = "gjennomforing_id", type = "STRING", description = "ID til gjennomføring (fra Valp)." },
    { mode = "NULLABLE", name = "start_dato", type = "DATE", description = "Startdato for deltakelse." },
    { mode = "NULLABLE", name = "slutt_dato", type = "DATE", description = "Sluttdato for deltakelse." },
    { mode = "NULLABLE", name = "dager_per_uke", type = "FLOAT", description = "Antall dager pr uke deltaker deltar." },
    { mode = "NULLABLE", name = "prosent_stilling", type = "FLOAT", description = "Deltakelsesprosent." },
    { mode = "NULLABLE", name = "registrert_dato", type = "TIMESTAMP", description = "Dato deltakeren ble påmeldt." },
    { mode = "NULLABLE", name = "kilde", type = "STRING", description = "Hvor deltakeren opprinnelig ble opprettet - ARENA eller KOMET." },
    { mode = "NULLABLE", name = "person_ident", type = "STRING", description = "Fødselsnummer til personen som deltar." },
    { mode = "NULLABLE", name = "status", type = "STRING", description = "Status for deltakeren." },
    { mode = "NULLABLE", name = "aarsak", type = "STRING", description = "Statusårsak." },
    { mode = "NULLABLE", name = "gyldig_fra", type = "TIMESTAMP", description = "Tidspunktet statusen er gyldig fra." }
  ])

  view_query = <<EOF
SELECT
  deltaker.id,
  deltaker.bruker_id,
  deltaker.gjennomforing_id,
  deltaker.start_dato,
  deltaker.slutt_dato,
  deltaker.dager_per_uke,
  deltaker.prosent_stilling,
  deltaker.registrert_dato,
  deltaker.kilde,
  bruker.person_ident,
  status.status,
  status.aarsak,
  status.gyldig_fra
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker` deltaker
INNER JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_bruker` bruker
ON deltaker.bruker_id = bruker.id
INNER JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_status` status
ON deltaker.id = status.deltaker_id
WHERE status.aktiv = true
EOF
}

module "amttiltak_deltaker_status_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "amttiltak_deltaker_status_view"

  view_schema = jsonencode([
    { mode = "NULLABLE", name = "deltaker_id", type = "STRING", description = "Unik ID for deltaker." },
    { mode = "NULLABLE", name = "status", type = "STRING", description = "Status for deltakeren." },
    { mode = "NULLABLE", name = "aarsak", type = "STRING", description = "Statusårsak." },
    { mode = "NULLABLE", name = "gyldig_fra", type = "TIMESTAMP", description = "Tidspunktet statusen er gyldig fra." },
    { mode = "NULLABLE", name = "aktiv", type = "BOOLEAN", description = "Hvorvidt statusen er aktiv (nåværende status)." }
  ])

  view_query = <<EOF
SELECT
  deltaker_id,
  status,
  aarsak,
  gyldig_fra,
  aktiv
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_status`
EOF
}
