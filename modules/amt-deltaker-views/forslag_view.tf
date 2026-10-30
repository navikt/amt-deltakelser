module "forslag_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "forslag_view"

  view_schema = jsonencode([
    { name = "forslag_id", type = "STRING", mode = "NULLABLE" },
    { name = "forslag_opprettet", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "forslag_endring", type = "JSON", mode = "NULLABLE" },
    { name = "forslag_status_type", type = "STRING", mode = "NULLABLE" },
    { name = "forslag_status", type = "JSON", mode = "NULLABLE" },
    { name = "forslag_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "forslag_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE" },
    { name = "deltaker_dager_per_uke", type = "FLOAT", mode = "NULLABLE" },
    { name = "deltaker_deltakelsesprosent", type = "FLOAT", mode = "NULLABLE" },
    { name = "deltaker_bakgrunnsinformasjon", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_innhold", type = "JSON", mode = "NULLABLE" },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_aarsak", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_status_gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltakerliste_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_navn", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_status", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_start_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltakerliste_slutt_dato", type = "DATE", mode = "NULLABLE" },
    { name = "deltakerliste_oppstart", type = "STRING", mode = "NULLABLE" },
    { name = "deltakerliste_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltakerliste_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE" },
    { name = "arrangor_id", type = "STRING", mode = "NULLABLE" },
    { name = "arrangor_navn", type = "STRING", mode = "NULLABLE" },
    { name = "arrangor_orgnr", type = "STRING", mode = "NULLABLE" },
    { name = "overordnet_arrangor_id", type = "STRING", mode = "NULLABLE" },
    { name = "overordnet_arrangor_navn", type = "STRING", mode = "NULLABLE" },
    { name = "overordnet_arrangor_orgnr", type = "STRING", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
select f.id as forslag_id,
  f.opprettet as forslag_opprettet,
  f.endring as forslag_endring,
  JSON_EXTRACT_SCALAR(f.status, "$.type") as forslag_status_type,
  f.status as forslag_status,
  f.created_at as forslag_created_at,
  f.modified_at as forslag_modified_at,
  d.*
from `${var.dataset_id}.deltaker_view` d join `${var.dataset_id}.public_forslag` f on d.deltaker_id = f.deltaker_id
EOF
  )
}
