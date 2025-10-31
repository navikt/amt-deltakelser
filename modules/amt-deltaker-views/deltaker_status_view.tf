module "deltaker_status_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "deltaker_status_view"

  view_schema = jsonencode([
    { name = "status_id", type = "STRING", mode = "NULLABLE", description = "Unik ID for deltakerstatus." },
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker." },
    { name = "status_type", type = "STRING", mode = "NULLABLE", description = "Status type (DELTAR, AVSLUTTET osv)." },
    { name = "status_aarsak", type = "STRING", mode = "NULLABLE", description = "Årsak til statusendring." },
    { name = "status_gyldig_fra", type = "DATE", mode = "NULLABLE", description = "Gyldig fra dato for status." },
    { name = "status_gyldig_til", type = "DATE", mode = "NULLABLE", description = "Gyldig til dato for status." },
    { name = "status_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for status." },
    { name = "status_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Sist endret tidspunkt for status." },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE", description = "Kilde for deltaker (ARENA, KOMET osv)." },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE", description = "Tiltakskode for deltakerens tiltak." }
  ])

  view_query = <<EOF
SELECT
  ds.id AS status_id,
  ds.deltaker_id AS deltaker_id,
  ds.type AS status_type,
  ds.aarsak AS status_aarsak,
  ds.gyldig_fra AS status_gyldig_fra,
  ds.gyldig_til AS status_gyldig_til,
  ds.created_at AS status_created_at,
  ds.modified_at AS status_modified_at,
  d.deltaker_kilde AS deltaker_kilde,
  d.tiltak_tiltakskode AS tiltak_tiltakskode
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.deltaker_view` d
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_status` ds
  ON d.deltaker_id = ds.deltaker_id
EOF
}
