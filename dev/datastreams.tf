locals {
  datastream_vpc_resources = {
    vpc_name                       = google_compute_network.amt_datastream_private_vpc.name
    private_connection_id          = google_datastream_private_connection.amt_datastream_private_connection.id
    bigquery_connection_profile_id = google_datastream_connection_profile.datastream_bigquery_connection_profile.id
  }
}

module "amttiltak_datastream" {
  source                              = "../modules/google-bigquery-datastream"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-tiltak"
  cloud_sql_instance_name             = "amt-tiltak-v1"
  cloud_sql_instance_db_name          = "amt-tiltak"
  cloud_sql_instance_db_credentials   = local.amttiltak_datastream_credentials
  cloud_sql_instance_publication_name = "amttiltak_publication"
  cloud_sql_instance_replication_name = "amttiltak_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_include_schemas          = [{ schema = "public", tables = [{ table = "deltaker_status" }, { table = "bruker", columns = ["id", "person_ident"] }, { table = "deltaker", columns = ["id", "bruker_id", "gjennomforing_id", "start_dato", "slutt_dato", "dager_per_uke", "prosent_stilling", "registrert_dato", "created_at", "modified_at", "kilde"] }] }]
  postgresql_exclude_schemas          = [{ schema = "public", tables = [{ table = "flyway_schema_history" }, { table = "bruker", columns = ["adressebeskyttelse"] }] }]
}