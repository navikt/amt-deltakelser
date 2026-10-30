module "hendelse_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "hendelse"

  view_schema = jsonencode([
    { name = "hendelse_id", type = "STRING", mode = "NULLABLE" },
    { name = "hendelse_deltaker_id", type = "STRING", mode = "NULLABLE" },
    { name = "hendelse_ansvarlig", type = "STRING", mode = "NULLABLE" },
    { name = "hendelse_type", type = "STRING", mode = "NULLABLE" },
    { name = "hendelse_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "hendelse_distribusjonskanal", type = "STRING", mode = "NULLABLE" },
    { name = "hendelse_manuelloppfolging", type = "BOOLEAN", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
SELECT
  id as hendelse_id,
  deltaker_id as hendelse_deltaker_id,
  JSON_EXTRACT_SCALAR(ansvarlig, '$.type') AS  hendelse_ansvarlig,
  JSON_EXTRACT_SCALAR(payload, '$.type') as hendelse_type ,
  created_at as hendelse_created_at,
  distribusjonskanal as hendelse_distribusjonskanal,
  manuelloppfolging as hendelse_manuelloppfolging
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_hendelse`
EOF
  )
}
