module "deltaker_endring_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "deltaker_endring_view"

  view_schema = jsonencode([
    { name = "deltaker_endring_id", type = "STRING", mode = "NULLABLE", description = "ID for deltakerendring." },
    { name = "deltaker_endring_endring", type = "JSON", mode = "NULLABLE", description = "Endringsinnhold." },
    { name = "deltaker_endring_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt." },
    { name = "deltaker_endring_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt." },
    { name = "forslag_id", type = "STRING", mode = "NULLABLE", description = "ID for forslag." },
    { name = "forslag_opprettet", type = "TIMESTAMP", mode = "NULLABLE", description = "Forslag opprettet tidspunkt." },
    { name = "forslag_endring", type = "JSON", mode = "NULLABLE", description = "Forslag endring." },
    { name = "forslag_status", type = "JSON", mode = "NULLABLE", description = "Forslag status." },
    { name = "forslag_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Forslag opprettet tidspunkt." },
    { name = "forslag_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Forslag endret tidspunkt." },
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker." },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE", description = "Person-ID til deltaker." },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE", description = "Dato deltaker søkte om deltakelse." },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE", description = "Startdato for deltakelse." },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE", description = "Sluttdato for deltakelse." },
    { name = "deltaker_dager_per_uke", type = "FLOAT", mode = "NULLABLE", description = "Antall dager per uke deltaker deltar." },
    { name = "deltaker_deltakelsesprosent", type = "FLOAT", mode = "NULLABLE", description = "Deltakelsesprosent." },
    { name = "deltaker_bakgrunnsinformasjon", type = "STRING", mode = "NULLABLE", description = "Bakgrunnsinformasjon om deltaker." },
    { name = "deltaker_innhold", type = "JSON", mode = "NULLABLE", description = "Ytterligere deltakerdata." },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for deltaker." },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt for deltaker." },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE", description = "Kilde for deltakerdata." },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE", description = "Type deltakerstatus." },
    { name = "deltaker_status_aarsak", type = "STRING", mode = "NULLABLE", description = "Årsak for status." },
    { name = "deltaker_status_gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE", description = "Gyldig fra dato." },
    { name = "deltaker_status_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE", description = "Gyldig til dato." },
    { name = "deltaker_status_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Status opprettet tidspunkt." },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Status endret tidspunkt." },
    { name = "deltakerliste_id", type = "STRING", mode = "NULLABLE", description = "ID for deltakerliste." },
    { name = "deltakerliste_navn", type = "STRING", mode = "NULLABLE", description = "Navn på deltakerliste." },
    { name = "deltakerliste_status", type = "STRING", mode = "NULLABLE", description = "Status på deltakerliste." },
    { name = "deltakerliste_start_dato", type = "DATE", mode = "NULLABLE", description = "Startdato for deltakerliste." },
    { name = "deltakerliste_slutt_dato", type = "DATE", mode = "NULLABLE", description = "Sluttdato for deltakerliste." },
    { name = "deltakerliste_oppstart", type = "STRING", mode = "NULLABLE", description = "Oppstart informasjon." },
    { name = "deltakerliste_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt for deltakerliste." },
    { name = "deltakerliste_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for deltakerliste." },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE", description = "Tiltakskode fra deltakerliste." },
    { name = "arrangor_id", type = "STRING", mode = "NULLABLE", description = "ID for arrangør." },
    { name = "arrangor_navn", type = "STRING", mode = "NULLABLE", description = "Navn på arrangør." },
    { name = "arrangor_orgnr", type = "STRING", mode = "NULLABLE", description = "Organisasjonsnummer for arrangør." },
    { name = "overordnet_arrangor_id", type = "STRING", mode = "NULLABLE", description = "ID for overordnet arrangør." },
    { name = "overordnet_arrangor_navn", type = "STRING", mode = "NULLABLE", description = "Navn på overordnet arrangør." },
    { name = "overordnet_arrangor_orgnr", type = "STRING", mode = "NULLABLE", description = "Org.nr for overordnet arrangør." }
  ])

  view_query = <<EOF
SELECT
  e.id AS deltaker_endring_id,
  e.endring AS deltaker_endring_endring,
  e.created_at AS deltaker_endring_created_at,
  e.modified_at AS deltaker_endring_modified_at,
  f.id AS forslag_id,
  f.opprettet AS forslag_opprettet,
  f.endring AS forslag_endring,
  f.status AS forslag_status,
  f.created_at AS forslag_created_at,
  f.modified_at AS forslag_modified_at,
  d.*
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.deltaker_view` d
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_endring` e
  ON d.deltaker_id = e.deltaker_id
LEFT JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_forslag` f
  ON e.forslag_id = f.id
EOF
}
