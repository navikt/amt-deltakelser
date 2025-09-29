data "google_secret_manager_secret_version" "amttiltak_datastream_secret" {
  secret  = var.amttiltak_datastream_secret
  version = 2
}

data "google_secret_manager_secret_version" "amt_deltaker_datastream_secret" {
  secret  = var.amt_deltaker_datastream_secret
  version = 8
}

locals {
  amttiltak_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amttiltak_datastream_secret.secret_data
  )
  amt_deltaker_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amt_deltaker_datastream_secret.secret_data
  )
}
