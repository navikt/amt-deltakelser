module "hendelse_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "hendelse"

  view_schema = jsonencode([
    { name = "hendelse_id", type = "STRING", mode = "NULLABLE", description = "ID for hendelsen." },
    { name = "hendelse_deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker knyttet til hendelsen." },
    { name = "hendelse_ansvarlig", type = "STRING", mode = "NULLABLE", description = "Ansvarlig for hendelsen (type fra JSON)." },
    { name = "hendelse_type", type = "STRING", mode = "NULLABLE", description = "Type hendelse (fra payload JSON)." },
    { name = "hendelse_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt hendelsen ble opprettet." },
    { name = "hendelse_distribusjonskanal", type = "STRING", mode = "NULLABLE", description = "Distribusjonskanal for hendelsen." },
    { name = "hendelse_manuelloppfolging", type = "BOOLEAN", mode = "NULLABLE", description = "Om hendelsen krever manuell oppfølging." }
  ])

  view_query = <<EOF
SELECT
  id AS hendelse_id,
  deltaker_id AS hendelse_deltaker_id,
  JSON_EXTRACT_SCALAR(ansvarlig, '$.type') AS hendelse_ansvarlig,
  JSON_EXTRACT_SCALAR(payload, '$.type') AS hendelse_type,
  created_at AS hendelse_created_at,
  distribusjonskanal AS hendelse_distribusjonskanal,
  manuelloppfolging AS hendelse_manuelloppfolging
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_hendelse`
EOF
}
