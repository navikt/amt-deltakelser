module "vedtak_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "vedtak_view"

  view_schema = jsonencode([
    { name = "vedtak_id", type = "STRING", mode = "NULLABLE", description = "ID for vedtak." },
    { name = "vedtak_fattet", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt vedtak ble fattet." },
    { name = "vedtak_fattet_av_nav", type = "STRING", mode = "NULLABLE", description = "Hvem i NAV fattet vedtaket." },
    { name = "vedtak_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE", description = "Gyldig til-dato for vedtaket." },
    { name = "vedtak_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt vedtaket ble opprettet." },
    { name = "vedtak_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt vedtaket sist ble endret." },
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE", description = "ID for deltaker." },
    { name = "deltaker_person_id", type = "STRING", mode = "NULLABLE", description = "Intern person-ID til deltaker." },
    { name = "deltaker_innsokt_dato", type = "DATE", mode = "NULLABLE", description = "Dato deltaker ble innsøkt." },
    { name = "deltaker_startdato", type = "DATE", mode = "NULLABLE", description = "Startdato for deltakelse." },
    { name = "deltaker_sluttdato", type = "DATE", mode = "NULLABLE", description = "Sluttdato for deltakelse." },
    { name = "deltaker_dager_per_uke", type = "FLOAT", mode = "NULLABLE", description = "Antall dager per uke deltaker deltar." },
    { name = "deltaker_deltakelsesprosent", type = "FLOAT", mode = "NULLABLE", description = "Deltakelsesprosent." },
    { name = "deltaker_created_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt deltaker ble opprettet." },
    { name = "deltaker_modified_at", type = "TIMESTAMP", mode = "NULLABLE", description = "Tidspunkt deltaker sist ble endret." },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE", description = "Kilde for deltakeren (ARENA, KOMET etc)." },
    { name = "deltaker_status_type", type = "STRING", mode = "NULLABLE", description = "Type deltakerstatus." },
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
  v.id AS vedtak_id,
  v.fattet AS vedtak_fattet,
  v.fattet_av_nav AS vedtak_fattet_av_nav,
  v.gyldig_til AS vedtak_gyldig_til,
  v.created_at AS vedtak_created_at,
  v.modified_at AS vedtak_modified_at,
  d.*
FROM
  `${var.gcp_project["project"]}.${var.dataset_id}.deltaker_view` d
JOIN
  `${var.gcp_project["project"]}.${var.dataset_id}.public_vedtak` v
  ON d.deltaker_id = v.deltaker_id
EOF
}
