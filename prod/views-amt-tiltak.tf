module "amt_tiltak_views" {
  count = var.enable_amt_tiltak_datastream ? 1 : 0

  source      = "../modules/amt-tiltak-views"
  gcp_project = var.gcp_project
  dataset_id  = coalesce(module.amt_tiltak_datastream[0].dataset_id, "amt_tiltak_datastream")
}
