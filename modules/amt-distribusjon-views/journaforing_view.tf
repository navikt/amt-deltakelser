module "forslag_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "journaforing" # typo fra vedkommende som har opprettet view

  view_schema = jsonencode([
    { name = "journalforing_journalpost_id", type = "STRING", mode = "NULLABLE", description = "ID for journalpost." },
    { name = "journalforing_bestillingsid", type = "STRING", mode = "NULLABLE", description = "Bestillings-ID for journalføring." },
    { name = "journalforing_kan_ikke_distribueres", type = "BOOLEAN", mode = "NULLABLE", description = "Indikator for om journalpost ikke kan distribueres." },
    { name = "journalforing_kan_ikke_journalfores", type = "BOOLEAN", mode = "NULLABLE", description = "Indikator for om journalpost ikke kan journalføres." },
    { name = "journalforing_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for journalføring." },
    { name = "journalforing_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt for journalføring." },
    { name = "hendelse_id", type = "STRING", mode = "NULLABLE", description = "ID for hendelsen." },
    { name = "hendelse_deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker knyttet til hendelsen." },
    { name = "hendelse_ansvarlig", type = "STRING", mode = "NULLABLE", description = "Ansvarlig for hendelsen." },
    { name = "hendelse_type", type = "STRING", mode = "NULLABLE", description = "Type hendelse." },
    { name = "hendelse_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt hendelsen ble opprettet." },
    { name = "hendelse_distribusjonskanal", type = "STRING", mode = "NULLABLE", description = "Distribusjonskanal for hendelsen." },
    { name = "hendelse_manuelloppfolging", type = "BOOLEAN", mode = "NULLABLE", description = "Om hendelsen krever manuell oppfølging." }
  ])

  view_query = <<EOF
SELECT
  j.journalpost_id AS journalforing_journalpost_id,
  j.bestillingsid AS journalforing_bestillingsid,
  j.kan_ikke_distribueres AS journalforing_kan_ikke_distribueres,
  j.kan_ikke_journalfores AS journalforing_kan_ikke_journalfores,
  j.created_at AS journalforing_created_at,
  j.modified_at AS journalforing_modified_at,
  h.hendelse_id,
  h.hendelse_deltaker_id,
  h.hendelse_ansvarlig,
  h.hendelse_type,
  h.hendelse_created_at,
  h.hendelse_distribusjonskanal,
  h.hendelse_manuelloppfolging
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.hendelse` h
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_journalforingstatus` j
ON
  h.hendelse_id = j.hendelse_id
EOF
}
