module "forslag_view" {
  source              = "../google-bigquery-view"
  depends_on          = [module.hendelse_view]
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "journaforing" # typo fra vedkommende som har opprettet view

  view_schema = jsonencode([
    { name = "journalforing_journalpost_id", type = "STRING" },
    { name = "journalforing_bestillingsid", type = "STRING" },
    { name = "journalforing_kan_ikke_distribueres", type = "BOOLEAN" },
    { name = "journalforing_kan_ikke_journalfores", type = "BOOLEAN" },
    { name = "journalforing_created_at", type = "TIMESTAMP" },
    { name = "journalforing_modified_at", type = "TIMESTAMP" },
    { name = "hendelse_id", type = "STRING" },
    { name = "hendelse_deltaker_id", type = "STRING" },
    { name = "hendelse_ansvarlig", type = "STRING" },
    { name = "hendelse_type", type = "STRING" },
    { name = "hendelse_created_at", type = "TIMESTAMP" },
    { name = "hendelse_distribusjonskanal", type = "STRING" },
    { name = "hendelse_manuelloppfolging", type = "BOOLEAN" }
  ])

  view_query = chomp(<<EOF
SELECT
  j.journalpost_id as journalforing_journalpost_id,
  j.bestillingsid as journalforing_bestillingsid,
  j.kan_ikke_distribueres as journalforing_kan_ikke_distribueres,
  j.kan_ikke_journalfores as journalforing_kan_ikke_journalfores,
  j.created_at as journalforing_created_at,
  j.modified_at as journalforing_modified_at,
  h.hendelse_id,
  h.hendelse_deltaker_id,
  h.hendelse_ansvarlig,
  h.hendelse_type,
  h.hendelse_created_at,
  h.hendelse_distribusjonskanal,
  h.hendelse_manuelloppfolging,
FROM `${var.gcp_project["project"]}.${var.dataset_id}.hendelse` AS h
JOIN `${var.gcp_project["project"]}.${var.dataset_id}.public_journalforingstatus` AS j
  ON h.hendelse_id = j.hendelse_id
EOF
  )
}
