module "deltaker_status_view" {
  source              = "../google-bigquery-view"
  depends_on          = [module.deltaker_view]
  deletion_protection = true
  dataset_id          = var.dataset_id
  view_id             = "deltaker_status_view"

  view_schema = jsonencode([
    { name = "status_id", type = "STRING", mode = "NULLABLE" },
    { name = "deltaker_id", type = "STRING", mode = "NULLABLE" },
    { name = "status_type", type = "STRING", mode = "NULLABLE" },
    { name = "status_aarsak", type = "STRING", mode = "NULLABLE" },
    { name = "status_gyldig_fra", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "status_gyldig_til", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "status_created_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "status_modified_at", type = "TIMESTAMP", mode = "NULLABLE" },
    { name = "deltaker_kilde", type = "STRING", mode = "NULLABLE" },
    { name = "tiltak_tiltakskode", type = "STRING", mode = "NULLABLE" }
  ])

  view_query = chomp(<<EOF
select 
 ds.id as status_id,
 ds.deltaker_id as deltaker_id,
 ds.type as status_type,
 ds.aarsak as status_aarsak,
 ds.gyldig_fra as status_gyldig_fra,
 ds.gyldig_til as status_gyldig_til,
 ds.created_at as status_created_at,
 ds.modified_at as status_modified_at,
 d.deltaker_kilde as deltaker_kilde,
 d.tiltak_tiltakskode as tiltak_tiltakskode
from `${var.dataset_id}.deltaker_view` d 
    join `${var.dataset_id}.public_deltaker_status` ds on d.deltaker_id = ds.deltaker_id
EOF
  )
}
