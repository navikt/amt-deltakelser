module "endring_fra_arrangor_view" {
  source              = "../google-bigquery-view"
  depends_on          = [module.deltaker_view]
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "endring_fra_arrangor_view"

  view_schema = jsonencode([
    { name = "endring_fra_arrangor_id", type = "STRING" },
    { name = "endring_fra_arrangor_endring", type = "JSON" },
    { name = "endring_fra_arrangor_opprettet", type = "TIMESTAMP" },
    { name = "endring_fra_arrangor_created_at", type = "TIMESTAMP" },
    { name = "endring_fra_arrangor_modified_at", type = "TIMESTAMP" },
    { name = "deltaker_id", type = "STRING" },
    { name = "deltaker_person_id", type = "STRING" },
    { name = "deltaker_innsokt_dato", type = "DATE" },
    { name = "deltaker_startdato", type = "DATE" },
    { name = "deltaker_sluttdato", type = "DATE" },
    { name = "deltaker_dager_per_uke", type = "FLOAT" },
    { name = "deltaker_deltakelsesprosent", type = "FLOAT" },
    { name = "deltaker_bakgrunnsinformasjon", type = "STRING" },
    { name = "deltaker_innhold", type = "JSON" },
    { name = "deltaker_created_at", type = "TIMESTAMP" },
    { name = "deltaker_modified_at", type = "TIMESTAMP" },
    { name = "deltaker_kilde", type = "STRING" },
    { name = "deltaker_status_type", type = "STRING" },
    { name = "deltaker_status_aarsak", type = "STRING" },
    { name = "deltaker_status_gyldig_fra", type = "TIMESTAMP" },
    { name = "deltaker_status_gyldig_til", type = "TIMESTAMP" },
    { name = "deltaker_status_created_at", type = "TIMESTAMP" },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP" },
    { name = "deltakerliste_id", type = "STRING" },
    { name = "deltakerliste_navn", type = "STRING" },
    { name = "deltakerliste_status", type = "STRING" },
    { name = "deltakerliste_start_dato", type = "DATE" },
    { name = "deltakerliste_slutt_dato", type = "DATE" },
    { name = "deltakerliste_oppstart", type = "STRING" },
    { name = "deltakerliste_gjennomforingstype", type = "STRING" },
    { name = "deltakerliste_modified_at", type = "TIMESTAMP" },
    { name = "deltakerliste_created_at", type = "TIMESTAMP" },
    { name = "tiltak_tiltakskode", type = "STRING" },
    { name = "arrangor_id", type = "STRING" },
    { name = "arrangor_navn", type = "STRING" },
    { name = "arrangor_orgnr", type = "STRING" },
    { name = "overordnet_arrangor_id", type = "STRING" },
    { name = "overordnet_arrangor_navn", type = "STRING" },
    { name = "overordnet_arrangor_orgnr", type = "STRING" }
  ])

  view_query = chomp(<<EOF
select e.id as endring_fra_arrangor_id,
  e.endring as endring_fra_arrangor_endring,
  e.opprettet as endring_fra_arrangor_opprettet,
  e.created_at as endring_fra_arrangor_created_at,
  e.modified_at as endring_fra_arrangor_modified_at,
  d.*
from `${var.gcp_project["project"]}.${var.dataset_id}.deltaker_view` d
    join `${var.gcp_project["project"]}.${var.dataset_id}.public_endring_fra_arrangor` e on d.deltaker_id = e.deltaker_id
EOF
  )
}
