module "amt_tiltak_datastream_amttiltak_deltaker_siste_status_view" {
  source              = "../google-bigquery-view"
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "amt_tiltak_datastream_amttiltak_deltaker_siste_status_view"

  view_schema = jsonencode([
    { name = "person_ident", type = "BYTES", mode = "NULLABLE" },
    { name = "id", type = "STRING", mode = "NULLABLE" },
    { name = "bruker_id", type = "STRING", mode = "NULLABLE" },
    { name = "gjennomforing_id", type = "STRING", mode = "NULLABLE" },
    { name = "start_dato", type = "DATE", mode = "NULLABLE" },
    { name = "slutt_dato", type = "DATE", mode = "NULLABLE" },
    { name = "dager_per_uke", type = "FLOAT", mode = "NULLABLE" },
    { name = "prosent_stilling", type = "FLOAT", mode = "NULLABLE" },
    { name = "registrert_dato", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "kilde", type = "STRING", mode = "NULLABLE" },
    { name = "status", type = "STRING", mode = "NULLABLE" },
    { name = "aarsak", type = "STRING", mode = "NULLABLE" },
    { name = "gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE" },
  ])

  view_query = chomp(<<EOF
WITH gen_salt AS (
		SELECT GENERATE_UUID() AS salt
	) SELECT  SHA256(person_ident || gen_salt.salt) AS person_ident,I.* EXCEPT(person_ident) FROM `${var.gcp_project["project"]}.amt_tiltak_datastream.amttiltak_deltaker_siste_status_view` AS I, gen_salt
EOF
  )
}
