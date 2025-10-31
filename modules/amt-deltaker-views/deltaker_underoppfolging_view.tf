module "deltaker_underoppfolging_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "deltaker_underoppfolging_view"

  view_schema = jsonencode([
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker." },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE", description = "Person-ID til deltaker." },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE", description = "Dato deltaker ble innsøkt." },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE", description = "Startdato." },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE", description = "Sluttdato." },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt." },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt." },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE", description = "Kilde." },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE", description = "Status type." },
    { name = "deltaker_status_gyldig_fra", type = "DATE", mode = "NULLABLE", description = "Status gyldig fra." },
    { name = "deltaker_status_gyldig_til", type = "DATE", mode = "NULLABLE", description = "Status gyldig til." },
    { name = "deltaker_status_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Status opprettet." },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Status endret." },
    { name = "deltakerliste_id", type = "STRING", mode = "NULLABLE", description = "ID deltakerliste." },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE", description = "Tiltakskode." },
    { name = "navbruker_innsatsgruppe", type = "STRING", mode = "NULLABLE", description = "NAV innsatsgruppe." },
    { name = "navbruker_oppfolgingsperioder", type = "STRING", mode = "NULLABLE", description = "Oppfølgingsperioder." }
  ])

  view_query = <<EOF
SELECT
  d.id AS deltaker_id,
  d.person_id AS deltaker_person_id,
  COALESCE(
    DATE(v.created_at),
    DATE(JSON_EXTRACT_SCALAR(ia.deltaker_ved_import, '$.innsoktDato'))
  ) AS deltaker_innsokt_dato,
  d.startdato AS deltaker_startdato,
  d.sluttdato AS deltaker_sluttdato,
  d.created_at AS deltaker_created_at,
  d.modified_at AS deltaker_modified_at,
  d.kilde AS deltaker_kilde,
  ds.type AS deltaker_status_type,
  ds.gyldig_fra AS deltaker_status_gyldig_fra,
  ds.gyldig_til AS deltaker_status_gyldig_til,
  ds.created_at AS deltaker_status_created_at,
  ds.modified_at AS deltaker_status_modified_at,
  dl.id AS deltakerliste_id,
  t.tiltakskode AS tiltak_tiltakskode,
  nb.innsatsgruppe AS navbruker_innsatsgruppe,
  nb.oppfolgingsperioder AS navbruker_oppfolgingsperioder
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker` d
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_status` ds ON d.id = ds.deltaker_id
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltakerliste` dl ON d.deltakerliste_id = dl.id
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_tiltakstype` t ON dl.tiltakstype_id = t.id
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_nav_bruker` nb ON d.person_id = nb.person_id
LEFT JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_vedtak` v ON v.deltaker_id = d.id
LEFT JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_importert_fra_arena` ia ON ia.deltaker_id = d.id
WHERE
  ds.gyldig_til IS NULL AND nb.adressebeskyttelse IS NULL
EOF
}
