module "deltaker_endring_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "deltaker_endring_view"

  view_schema = jsonencode([
    { name = "deltaker_endring_id", type = "STRING" },
    { name = "deltaker_endring_endring", type = "JSON" },
    { name = "deltaker_endring_created_at", type = "TIMESTAMP" },
    { name = "deltaker_endring_modified_at", type = "TIMESTAMP" },
    { name = "forslag_id", type = "STRING" },
    { name = "forslag_opprettet", type = "TIMESTAMP" },
    { name = "forslag_endring", type = "JSON" },
    { name = "forslag_status", type = "JSON" },
    { name = "forslag_created_at", type = "TIMESTAMP" },
    { name = "forslag_modified_at", type = "TIMESTAMP" },
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
select e.id as deltaker_endring_id,
  e.endring as deltaker_endring_endring,
  e.created_at as deltaker_endring_created_at,
  e.modified_at as deltaker_endring_modified_at,
  f.id as forslag_id,
  f.opprettet as forslag_opprettet,
  f.endring as forslag_endring,
  f.status as forslag_status,
  f.created_at as forslag_created_at,
  f.modified_at as forslag_modified_at,
  d.*
from `${var.dataset_id}.deltaker_view` d 
    join `${var.dataset_id}.public_deltaker_endring` e on d.deltaker_id = e.deltaker_id
    left join `${var.dataset_id}.public_forslag` f on e.forslag_id = f.id
EOF
  )
}
