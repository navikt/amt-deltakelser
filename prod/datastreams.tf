locals {
  datastream_vpc_resources = {
    vpc_name                       = module.amt_datastream_vpc.vpc_name
    private_connection_id          = module.amt_datastream_vpc.private_connection_id
    bigquery_connection_profile_id = module.amt_datastream_vpc.bigquery_connection_profile_id
  }
}

module "amt_datastream_vpc" {
  source      = "../modules/datastream-vpc"
  gcp_project = var.gcp_project
}

module "amt_deltaker_datastream" {
  count = var.enable_amt_deltaker_datastream ? 1 : 0

  source                              = "git::https://github.com/navikt/terraform-google-bigquery-datastream.git?ref=v1.0.4"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-deltaker"
  cloud_sql_instance_name             = "amt-deltaker"
  cloud_sql_instance_db_name          = "amt-deltaker"
  cloud_sql_instance_db_credentials   = local.amt_deltaker_datastream_credentials
  cloud_sql_instance_publication_name = "amt_deltaker_publication"
  cloud_sql_instance_replication_name = "amt_deltaker_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_exclude_schemas          = [{ schema = "public", tables = [{ table = "flyway_schema_history" }, { table = "nav_bruker", columns = ["fornavn", "mellomnavn", "etternavn", "telefonnummer", "epost", "adresse", "er_skjermet"] }, { table = "nav_ansatt" }] }]
  cloud_sql_proxy_vm_machine_type     = "e2-micro"
  authorized_views                    = []
}

module "amt_distribusjon_datastream" {
  count = var.enable_amt_distribusjon_datastream ? 1 : 0

  source                              = "git::https://github.com/navikt/terraform-google-bigquery-datastream.git?ref=v1.0.4"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-distribusjon"
  cloud_sql_instance_name             = "amt-distribusjon"
  cloud_sql_instance_db_name          = "amt-distribusjon"
  cloud_sql_instance_db_credentials   = local.amt_distribusjon_datastream_credentials
  cloud_sql_instance_publication_name = "amt_distribusjon_publication"
  cloud_sql_instance_replication_name = "amt_distribusjon_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_exclude_schemas          = [{ schema = "public", tables = [{ table = "flyway_schema_history" }] }]
  cloud_sql_proxy_vm_machine_type     = "e2-micro"
  authorized_views                    = []
}

module "amttiltak_datastream" {
  count = var.enable_amt_tiltak_datastream ? 1 : 0

  source                              = "git::https://github.com/navikt/terraform-google-bigquery-datastream.git?ref=v1.0.4"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-tiltak"
  cloud_sql_instance_name             = "amt-tiltak"
  cloud_sql_instance_db_name          = "amt-tiltak"
  cloud_sql_instance_db_credentials   = local.amttiltak_datastream_credentials
  cloud_sql_instance_publication_name = "amttiltak_publication"
  cloud_sql_instance_replication_name = "amttiltak_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_include_schemas          = [{ schema = "public", tables = [{ table = "deltaker_status" }, { table = "bruker", columns = ["id", "person_ident"] }, { table = "deltaker", columns = ["id", "bruker_id", "gjennomforing_id", "start_dato", "slutt_dato", "dager_per_uke", "prosent_stilling", "registrert_dato", "created_at", "modified_at", "kilde"] }, { table = "gjennomforing" }] }]
  postgresql_exclude_schemas          = [{ schema = "public", tables = [{ table = "flyway_schema_history" }, { table = "bruker", columns = ["adressebeskyttelse"] }] }]
  cloud_sql_proxy_vm_machine_type     = "e2-micro"

  authorized_views = [
    {
      view = {
        dataset_id = "amt_tiltak_datastream"
        project_id = var.gcp_project["project"]
        table_id   = "amttiltak_deltaker_personident_view"
      }
    },
    {
      view = {
        dataset_id = "amt_tiltak_datastream"
        project_id = var.gcp_project["project"]
        table_id   = "amttiltak_deltaker_siste_status_view"
      }
    },
    {
      view = {
        dataset_id = "amt_tiltak_datastream"
        project_id = var.gcp_project["project"]
        table_id   = "amttiltak_deltaker_status_view"
      }
    },
    {
      view = {
        dataset_id = "markedsplassen_pseudo"
        project_id = var.gcp_project["project"]
        table_id   = "amt_tiltak_datastream_amttiltak_deltaker_siste_status_view"
      }
    }
  ]
}
