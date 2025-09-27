module "amttiltak_views" {
  source      = "../modules/amttiltak-views"
  gcp_project = var.gcp_project
  dataset_id  = coalesce(module.amttiltak_datastream[0].dataset_id, "amt_tiltak_datastream")
}