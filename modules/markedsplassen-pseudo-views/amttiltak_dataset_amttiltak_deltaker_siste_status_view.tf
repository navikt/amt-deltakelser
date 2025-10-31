module "amttiltak_dataset_amttiltak_deltaker_siste_status_view" {
  source              = "../google-bigquery-view"
  deletion_protection = false
  dataset_id          = var.dataset_id
  view_id             = "amttiltak_dataset_amttiltak_deltaker_siste_status_view"

  view_schema = jsonencode([
    { name = "person_ident", type = "STRING", mode = "NULLABLE", description = "SHA256-hash av person_ident med salt." },
    { name = "id", type = "STRING", mode = "NULLABLE", description = "Unik ID for deltaker." },
    { name = "bruker_id", type = "STRING", mode = "NULLABLE", description = "Amt-ID til personen som deltar." },
    { name = "gjennomforing_id", type = "STRING", mode = "NULLABLE", description = "ID til gjennomføring." },
    { name = "start_dato", type = "DATE", mode = "NULLABLE", description = "Startdato for deltakelse." },
    { name = "slutt_dato", type = "DATE", mode = "NULLABLE", description = "Sluttdato for deltakelse." },
    { name = "dager_per_uke", type = "FLOAT", mode = "NULLABLE", description = "Antall dager pr uke deltaker deltar." },
    { name = "prosent_stilling", type = "FLOAT", mode = "NULLABLE", description = "Deltakelsesprosent." },
    { name = "registrert_dato", type = "TIMESTAMP", mode = "NULLABLE", description = "Dato deltaker ble registrert." },
    { name = "status", type = "STRING", mode = "NULLABLE", description = "Status for deltaker." },
    { name = "aarsak", type = "STRING", mode = "NULLABLE", description = "Årsak til status." },
    { name = "gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE", description = "Gyldig fra dato for status." },
  ])

  view_query = <<EOF
WITH gen_salt AS (
    SELECT GENERATE_UUID() AS salt
)
SELECT
    SHA256(person_ident || gen_salt.salt) AS person_ident,
    I.* EXCEPT(person_ident)
FROM
    `${var.gcp_project["project"]}.${var.dataset_id}.amttiltak_deltaker_siste_status_view` AS I,
    gen_salt
EOF
}
