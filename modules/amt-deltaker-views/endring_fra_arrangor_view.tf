module "endring_fra_arrangor_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "endring_fra_arrangor_view"

  view_schema = jsonencode([
    { name = "endring_fra_arrangor_id", type = "STRING", mode = "NULLABLE", description = "ID for endring fra arrangør." },
    { name = "endring_fra_arrangor_endring", type = "JSON", mode = "NULLABLE", description = "Endringsinnhold." },
    { name = "endring_fra_arrangor_opprettet", type = "TIMESTAMP", mode = "NULLABLE", description = "Opprettet tidspunkt." },
    { name = "endring_fra_arrangor_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt for opprettelse." },
    { name = "endring_fra_arrangor_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt for endring." },
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
  e.id AS endring_fra_arrangor_id,
  e.endring AS endring_fra_arrangor_endring,
  e.opprettet AS endring_fra_arrangor_opprettet,
  e.created_at AS endring_fra_arrangor_created_at,
  e.modified_at AS endring_fra_arrangor_modified_at,
  d.*
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.deltaker_view` d
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_endring_fra_arrangor` e
  ON d.deltaker_id = e.deltaker_id
EOF
}
