module "amt_distribusjon_views" {
  count = var.enable_amt_distribusjon_datastream ? 1 : 0

  source      = "../modules/amt-distribusjon-views"
  gcp_project = var.gcp_project
  dataset_id  = coalesce(module.amt_distribusjon_datastream[0].dataset_id, "amt_distribusjon_datastream")
}

module "amt_deltaker_views" {
  count = var.enable_amt_deltaker_datastream ? 1 : 0

  source      = "../modules/amt-deltaker-views"
  gcp_project = var.gcp_project
  dataset_id  = coalesce(module.amt_deltaker_datastream[0].dataset_id, "amt_deltaker_datastream")
}
