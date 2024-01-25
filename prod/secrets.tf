data "google_secret_manager_secret_version" "amttiltak_datastream_secret" {
  secret = var.amttiltak_datastream_secret
}

locals {
  amttiltak_datastream_credentials = jsondecode(
    data.google_secret_manager_secret_version.amttiltak_datastream_secret.secret_data
  )
}