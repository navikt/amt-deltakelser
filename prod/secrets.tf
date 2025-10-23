data "google_secret_manager_secret_version" "amttiltak_datastream_secret" {
  secret  = var.amt_tiltak_datastream_secret
}

data "google_secret_manager_secret_version" "amt_deltaker_datastream_secret" {
  secret  = var.amt_deltaker_datastream_secret
}

data "google_secret_manager_secret_version" "amt_distribusjon_datastream_secret" {
  secret  = var.amt_distribusjon_datastream_secret
}

locals {
  amttiltak_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amttiltak_datastream_secret.secret_data
  )
  amt_deltaker_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amt_deltaker_datastream_secret.secret_data
  )
  amt_distribusjon_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amt_distribusjon_datastream_secret.secret_data
  )
}