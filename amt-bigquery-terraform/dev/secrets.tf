data "google_secret_manager_secret_version" "amt_deltaker_datastream_secret" {
  count  = var.enable_amt_deltaker_datastream ? 1 : 0
  secret = var.amt_deltaker_datastream_secret
}

locals {
  amt_deltaker_datastream_credentials = var.enable_amt_deltaker_datastream ? jsondecode(
    data.google_secret_manager_secret_version.amt_deltaker_datastream_secret[0].secret_data
  ) : null
}