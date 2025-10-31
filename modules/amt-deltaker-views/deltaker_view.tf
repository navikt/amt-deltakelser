module "deltaker_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "deltaker_view"

  view_schema = jsonencode([
    { name = "deltaker_endring_id", type = "STRING", mode = "NULLABLE", description = "ID for deltakerendring." },
    { name = "deltaker_endring_endring", type = "JSON", mode = "NULLABLE", description = "Endringsinnhold." },
    { name = "deltaker_endring_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for deltakerendring." },
    { name = "deltaker_endring_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt for deltakerendring." },
    { name = "forslag_id", type = "STRING", mode = "NULLABLE", description = "ID for forslag." },
    { name = "forslag_opprettet", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt for forslag opprettet." },
    { name = "forslag_endring", type = "JSON", mode = "NULLABLE", description = "Endring i forslag." },
    { name = "forslag_status", type = "JSON", mode = "NULLABLE", description = "Status på forslag." },
    { name = "forslag_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt forslag ble opprettet." },
    { name = "forslag_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt forslag ble endret." },
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker." },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE", description = "Intern person-ID til deltaker." },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE", description = "Dato deltaker ble innsøkt." },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE", description = "Startdato for deltakelse." },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE", description = "Sluttdato for deltakelse." },
    { name = "deltaker_dager_per_uke", type = "FLOAT", mode = "NULLABLE", description = "Antall dager per uke deltaker deltar." },
    { name = "deltaker_deltakelsesprosent", type = "FLOAT", mode = "NULLABLE", description = "Deltakelsesprosent." },
    { name = "deltaker_bakgrunnsinformasjon", type = "STRING", mode = "NULLABLE", description = "Bakgrunnsinformasjon om deltaker." },
    { name = "deltaker_innhold", type = "JSON", mode = "NULLABLE", description = "Innhold i tiltaket." },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt deltaker ble opprettet." },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt deltaker sist ble endret." },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE", description = "Kilde for deltakeren (ARENA, KOMET etc)." },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE", description = "Type deltakerstatus (DELTAR, AVSLUTTET etc.)." },
    { name = "deltaker_status_aarsak", type = "STRING", mode = "NULLABLE", description = "Årsak til statusendring." },
    { name = "deltaker_status_gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE", description = "Gyldig fra dato for status." },
    { name = "deltaker_status_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE", description = "Gyldig til dato for status." },
    { name = "deltaker_status_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for status." },
    { name = "deltaker_status_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt for status." },
    { name = "deltakerliste_id", type = "STRING", mode = "NULLABLE", description = "ID til deltakerlisten deltakeren tilhører." },
    { name = "deltakerliste_navn", type = "STRING", mode = "NULLABLE", description = "Navn på deltakerlisten." },
    { name = "deltakerliste_status", type = "STRING", mode = "NULLABLE", description = "Status på deltakerlisten." },
    { name = "deltakerliste_start_dato", type = "DATE", mode = "NULLABLE", description = "Startdato for deltakerlisten." },
    { name = "deltakerliste_slutt_dato", type = "DATE", mode = "NULLABLE", description = "Sluttdato for deltakerlisten." },
    { name = "deltakerliste_oppstart", type = "STRING", mode = "NULLABLE", description = "Oppstartsinformasjon deltakerliste." },
    { name = "deltakerliste_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Endret tidspunkt for deltakerlisten." },
    { name = "deltakerliste_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt for deltakerlisten." },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE", description = "Tiltakskode for tiltaksvarianten." },
    { name = "arrangor_id", type = "STRING", mode = "NULLABLE", description = "ID til arrangør." },
    { name = "arrangor_navn", type = "STRING", mode = "NULLABLE", description = "Navn på arrangør." },
    { name = "arrangor_orgnr", type = "STRING", mode = "NULLABLE", description = "Organisasjonsnummer til arrangør." },
    { name = "overordnet_arrangor_id", type = "STRING", mode = "NULLABLE", description = "ID for overordnet arrangør." },
    { name = "overordnet_arrangor_navn", type = "STRING", mode = "NULLABLE", description = "Navn på overordnet arrangør." },
    { name = "overordnet_arrangor_orgnr", type = "STRING", mode = "NULLABLE", description = "Organisasjonsnummer for overordnet arrangør." }
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
  d.dager_per_uke AS deltaker_dager_per_uke,
  d.deltakelsesprosent AS deltaker_deltakelsesprosent,
  d.bakgrunnsinformasjon AS deltaker_bakgrunnsinformasjon,
  d.innhold AS deltaker_innhold,
  d.created_at AS deltaker_created_at,
  d.modified_at AS deltaker_modified_at,
  d.kilde AS deltaker_kilde,
  ds.type AS deltaker_status_type,
  ds.aarsak AS deltaker_status_aarsak,
  ds.gyldig_fra AS deltaker_status_gyldig_fra,
  ds.gyldig_til AS deltaker_status_gyldig_til,
  dl.id AS deltakerliste_id,
  dl.navn AS deltakerliste_navn,
  t.tiltakskode AS tiltak_tiltakskode,
  a.id AS arrangor_id,
  a.navn AS arrangor_navn,
  a.organisasjonsnummer AS arrangor_orgnr,
  oa.id AS overordnet_arrangor_id,
  oa.navn AS overordnet_arrangor_navn
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker` d
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltaker_status` ds ON d.id = ds.deltaker_id
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_deltakerliste` dl ON d.deltakerliste_id = dl.id
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_tiltakstype` t ON dl.tiltakstype_id = t.id
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_arrangor` a ON dl.arrangor_id = a.id
LEFT JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_arrangor` oa ON a.overordnet_arrangor_id = oa.id
LEFT JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_vedtak` v ON v.deltaker_id = d.id
LEFT JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_importert_fra_arena` ia ON ia.deltaker_id = d.id
WHERE
  ds.gyldig_til IS NULL
EOF
}
