# Modulen google-bigquery-connection var ikkes brukt tidligere, usikkert om den skal med
/*module "amt_tiltak_bigquery_connection" {
  count = var.enable_amt_tiltak_datastream ? 1 : 0

  source        = "../modules/google-bigquery-connection"
  connection_id = "amt-tiltak"
  location      = var.gcp_project["region"]
  instance_id   = "${var.gcp_project["project"]}:${var.gcp_project["region"]}:amt-tiltak"
  database      = "amt-tiltak"
  username      = local.amttiltak_datastream_credentials["username"]
  password      = local.amttiltak_datastream_credentials["password"]
}

module "amt_deltaker_bigquery_connection" {
  count = var.enable_amt_deltaker_datastream ? 1 : 0

  source        = "../modules/google-bigquery-connection"
  connection_id = "amt-deltaker"
  location      = var.gcp_project["region"]
  instance_id   = "${var.gcp_project["project"]}:${var.gcp_project["region"]}:amt-deltaker"
  database      = "amt-deltaker"
  username      = local.amt_deltaker_datastream_credentials["username"]
  password      = local.amt_deltaker_datastream_credentials["password"]
}*/