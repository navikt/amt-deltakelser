data "google_secret_manager_secret_version" "amt_deltaker_datastream_secret" {
  secret = var.amt_deltaker_datastream_secret
}

locals {
  amt_deltaker_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amt_deltaker_datastream_secret.secret_data
  )
}