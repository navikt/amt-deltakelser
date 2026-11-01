locals {
  datastream_vpc_resources = var.enable_amt_deltaker_datastream ? {
    vpc_name                       = module.amt_datastream_vpc[0].vpc_name
    private_connection_id          = module.amt_datastream_vpc[0].private_connection_id
    bigquery_connection_profile_id = module.amt_datastream_vpc[0].bigquery_connection_profile_id
  } : null
}

moved {
  from = module.amt_datastream_vpc
  to   = module.amt_datastream_vpc[0]
}

module "amt_datastream_vpc" {
  count = var.enable_amt_deltaker_datastream ? 1 : 0

  source      = "../modules/datastream-vpc"
  gcp_project = var.gcp_project
}

module "amt_deltaker_datastream" {
  count = var.enable_amt_deltaker_datastream ? 1 : 0

  source                              = "git::https://github.com/navikt/terraform-google-bigquery-datastream.git?ref=v1.0.10"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-deltaker"
  cloud_sql_instance_name             = "amt-deltaker-2"
  cloud_sql_instance_db_name          = "amt-deltaker"
  cloud_sql_instance_db_credentials   = local.amt_deltaker_datastream_credentials
  cloud_sql_instance_publication_name = "ds_publication"
  cloud_sql_instance_replication_name = "ds_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_exclude_schemas          = [{ schema = "public", tables = [{ table = "flyway_schema_history" }, { table = "nav_bruker", columns = ["fornavn", "mellomnavn", "etternavn", "telefonnummer", "epost", "adresse", "er_skjermet"] }, { table = "nav_ansatt" }] }]
  cloud_sql_proxy_vm_machine_type     = "e2-micro"
  authorized_views                    = []
}
