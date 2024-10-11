locals {
  datastream_vpc_resources = {
    vpc_name                       = google_compute_network.amt_datastream_private_vpc.name
    private_connection_id          = google_datastream_private_connection.amt_datastream_private_connection.id
    bigquery_connection_profile_id = google_datastream_connection_profile.datastream_bigquery_connection_profile.id
  }
}

module "amttiltak_datastream" {
  source                            = "../modules/google-bigquery-datastream"
  gcp_project                       = var.gcp_project
  application_name                  = "amt-tiltak"
  cloud_sql_instance_name           = "amt-tiltak-v1"
  cloud_sql_instance_db_name        = "amt-tiltak"
  cloud_sql_instance_db_credentials = local.amttiltak_datastream_credentials
  datastream_vpc_resources          = local.datastream_vpc_resources
}