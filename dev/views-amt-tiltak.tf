module "amttiltak_deltaker_personident_view" {
  source = "../modules/google-bigquery-view"

  dataset_id = google_bigquery_dataset.amttiltak_dataset.dataset_id
  view_id    = "amttiltak_deltaker_personident_view"
  view_schema = jsonencode(
    [
      {
        mode        = "NULLABLE"
        name        = "id"
        type        = "STRING"
        description = "Unik ID for deltaker."
      },
      {
        mode        = "NULLABLE"
        name        = "bruker_id"
        type        = "STRING"
        description = "Amt-ID til personen som deltar."
      },
      {
        mode        = "NULLABLE"
        name        = "gjennomforing_id"
        type        = "STRING"
        description = "ID til gjennomføring (fra Valp)."
      },
      {
        mode        = "NULLABLE"
        name        = "start_dato"
        type        = "DATE"
        description = "Startdato for deltakelse."
      },
      {
        mode        = "NULLABLE"
        name        = "slutt_dato"
        type        = "DATE"
        description = "Sluttdato for deltakelse."
      },
      {
        mode        = "NULLABLE"
        name        = "dager_per_uke"
        type        = "FLOAT"
        description = "Antall dager pr uke deltaker deltar."
      },
      {
        mode        = "NULLABLE"
        name        = "prosent_stilling"
        type        = "FLOAT"
        description = "Deltakelsesprosent."
      },
      {
        mode        = "NULLABLE"
        name        = "registrert_dato"
        type        = "DATE"
        description = "Dato deltakeren ble påmeldt."
      },
      {
        mode        = "NULLABLE"
        name        = "person_ident"
        type        = "STRING"
        description = "Fødselsnummer til personen som deltar."
      },
    ]
  )

  view_query = <<EOF
SELECT
  deltaker.id,
  deltaker.bruker_id,
  deltaker.gjennomforing_id,
  deltaker.start_dato,
  deltaker.slutt_dato,
  deltaker.dager_per_uke,
  deltaker.prosent_stilling,
  deltaker.registrert_dato,
  bruker.person_ident
FROM
  `${var.gcp_project["project"]}.${google_bigquery_dataset.amttiltak_dataset.dataset_id}.public_deltaker` deltaker
INNER JOIN
  `${var.gcp_project["project"]}.${google_bigquery_dataset.amttiltak_dataset.dataset_id}.public_bruker` bruker
ON
  deltaker.bruker_id = bruker.id
EOF

}