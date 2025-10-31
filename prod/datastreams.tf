locals {
  datastream_vpc_resources = {
    vpc_name                       = module.amt_datastream_vpc.vpc_name
    private_connection_id          = module.amt_datastream_vpc.private_connection_id
    bigquery_connection_profile_id = module.amt_datastream_vpc.bigquery_connection_profile_id
  }

  access_roles = [
    {
      role          = "OWNER"
      special_group = "projectOwners"
    },
    {
      role          = "READER"
      special_group = "projectReaders"
    },
    {
      role          = "WRITER"
      special_group = "projectWriters"
    },
    {
      role           = "roles/bigquery.metadataViewer"
      group_by_email = "all-users@nav.no"
    },
    {
      role          = "roles/bigquery.metadataViewer"
      user_by_email = "nada-metabase@nada-prod-6977.iam.gserviceaccount.com"
    },
  ]

  deltaker_view_ids = [
    "deltaker_endring_view",
    "deltaker_status_view",
    "deltaker_underoppfolging_view",
    "deltaker_view",
    "endring_fra_arrangor_view",
    "endring_fra_tiltakskoordinator_view",
    "forlengelse_forslag_behandlet_for_sluttdato_view",
    "forlengelse_forslag_sendt_ift_sluttdato",
    "forslag_view",
    "vedtak_view",
  ]

  distribusjon_view_ids = [
    "hendelse",
    "journaforing",
    "public_tiltakshendelse",
    "public_varsel",
  ]

  tiltak_view_ids = [
    "amttiltak_deltaker_personident_view",
    "amttiltak_deltaker_siste_status_view",
    "amttiltak_deltaker_status_view",
  ]

  markedsplassen_pseudo_view_ids = [
    "amt_tiltak_datastream_amttiltak_deltaker_siste_status_view",
    "amttiltak_dataset_amttiltak_deltaker_personident_view",
    "amttiltak_dataset_amttiltak_deltaker_siste_status_view",
  ]
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
  cloud_sql_instance_name             = "amt-deltaker-2"
  cloud_sql_instance_db_name          = "amt-deltaker"
  cloud_sql_instance_db_credentials   = local.amt_deltaker_datastream_credentials
  cloud_sql_instance_publication_name = "ds_publication"
  cloud_sql_instance_replication_name = "ds_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_include_schemas = [
    {
      schema = "public"
      tables = [
        { table = "arrangor", columns = ["id", "navn", "organisasjonsnummer", "overordnet_arrangor_id"] },
        { table = "deltaker", columns = ["id", "person_id", "startdato", "sluttdato", "dager_per_uke", "deltakelsesprosent", "bakgrunnsinformasjon", "innhold", "deltakerliste_id", "created_at", "modified_at", "kilde"] },
        { table = "deltaker_endring", columns = ["id", "deltaker_id", "forslag_id", "endring", "created_at", "modified_at"] },
        { table = "deltaker_status", columns = ["id", "deltaker_id", "type", "aarsak", "gyldig_fra", "gyldig_til", "created_at", "modified_at"] },
        { table = "deltakerliste", columns = ["id", "navn", "status", "start_dato", "slutt_dato", "oppstart", "tiltakstype_id", "arrangor_id", "created_at", "modified_at"] },
        { table = "endring_fra_arrangor", columns = ["id", "deltaker_id", "endring", "opprettet", "created_at", "modified_at"] },
        { table = "endring_fra_tiltakskoordinator", columns = ["id", "deltaker_id", "endret", "endring", "created_at", "modified_at"] },
        { table = "forslag", columns = ["id", "deltaker_id", "opprettet", "endring", "status", "created_at", "modified_at"] },
        { table = "importert_fra_arena", columns = ["deltaker_id", "deltaker_ved_import", "importert_dato"] },
        { table = "nav_bruker", columns = ["person_id", "innsatsgruppe", "oppfolgingsperioder", "adressebeskyttelse"] },
        { table = "tiltakstype", columns = ["id", "tiltakskode"] },
        { table = "vedtak", columns = ["id", "deltaker_id", "created_at"] },
      ]
    }
  ]

  cloud_sql_proxy_vm_machine_type = "e2-micro"
  access_roles                    = local.access_roles
  authorized_views = [
    for table_id in local.deltaker_view_ids : {
      view = {
        project_id = var.gcp_project["project"]
        dataset_id = "amt_deltaker_datastream"
        table_id   = table_id
      }
    }
  ]
}

module "amt_distribusjon_datastream" {
  count = var.enable_amt_distribusjon_datastream ? 1 : 0

  source                              = "git::https://github.com/navikt/terraform-google-bigquery-datastream.git?ref=v1.0.4"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-distribusjon"
  cloud_sql_instance_name             = "amt-distribusjon"
  cloud_sql_instance_db_name          = "amt-distribusjon"
  cloud_sql_instance_db_credentials   = local.amt_distribusjon_datastream_credentials
  cloud_sql_instance_publication_name = "ds_publication"
  cloud_sql_instance_replication_name = "ds_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_exclude_schemas = [
    { schema = "public", tables = [{ table = "flyway_schema_history" }, { table = "outbox_record" }] }
  ]
  cloud_sql_proxy_vm_machine_type = "e2-micro"
  access_roles                    = local.access_roles
  authorized_views = [
    for table_id in local.distribusjon_view_ids : {
      view = {
        project_id = var.gcp_project["project"]
        dataset_id = "amt_distribusjon_datastream"
        table_id   = table_id
      }
    }
  ]
}

module "amt_tiltak_datastream" {
  count = var.enable_amt_tiltak_datastream ? 1 : 0

  source                              = "git::https://github.com/navikt/terraform-google-bigquery-datastream.git?ref=v1.0.4"
  gcp_project                         = var.gcp_project
  application_name                    = "amt-tiltak"
  cloud_sql_instance_name             = "amt-tiltak"
  cloud_sql_instance_db_name          = "amt-tiltak"
  cloud_sql_instance_db_credentials   = local.amt_tiltak_datastream_credentials
  cloud_sql_instance_publication_name = "ds_publication"
  cloud_sql_instance_replication_name = "ds_replication"
  datastream_vpc_resources            = local.datastream_vpc_resources
  postgresql_include_schemas = [
    {
      schema = "public"
      tables = [
        { table = "deltaker_status" },
        { table = "bruker", columns = ["id", "person_ident"] },
        { table = "deltaker", columns = ["id", "bruker_id", "gjennomforing_id", "start_dato", "slutt_dato", "dager_per_uke", "prosent_stilling", "registrert_dato", "created_at", "modified_at", "kilde"] },
        { table = "gjennomforing" }
      ]
    }
  ]
  cloud_sql_proxy_vm_machine_type = "e2-micro"
  access_roles                    = local.access_roles
  authorized_views = concat(
    [
      for table_id in local.tiltak_view_ids : {
        view = {
          project_id = var.gcp_project["project"]
          dataset_id = "amt_tiltak_datastream"
          table_id   = table_id
        }
      }
    ],
    [
      for table_id in local.markedsplassen_pseudo_view_ids : {
        view = {
          project_id = var.gcp_project["project"]
          dataset_id = "markedsplassen_pseudo"
          table_id   = table_id
        }
      }
    ]
  )
}