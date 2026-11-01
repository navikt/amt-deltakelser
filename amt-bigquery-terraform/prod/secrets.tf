data "google_secret_manager_secret_version" "amt_deltaker_datastream_secret" {
  count  = var.enable_amt_deltaker_datastream ? 1 : 0
  secret = var.amt_deltaker_datastream_secret
}

data "google_secret_manager_secret_version" "amt_distribusjon_datastream_secret" {
  count  = var.enable_amt_distribusjon_datastream ? 1 : 0
  secret = var.amt_distribusjon_datastream_secret
}

locals {
  amt_deltaker_datastream_credentials = var.enable_amt_deltaker_datastream ? jsondecode(
    data.google_secret_manager_secret_version.amt_deltaker_datastream_secret[0].secret_data
  ) : null
  amt_distribusjon_datastream_credentials = var.enable_amt_distribusjon_datastream ? jsondecode(
    data.google_secret_manager_secret_version.amt_distribusjon_datastream_secret[0].secret_data
  ) : null
}