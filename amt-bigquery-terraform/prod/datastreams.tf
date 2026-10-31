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
    "public_arrangor",
    "public_deltaker",
    "public_deltaker_endring",
    "public_deltaker_status",
    "public_gjennomforing",
    "public_endring_fra_arrangor",
    "public_endring_fra_tiltakskoordinator",
    "public_forslag",
    "public_importert_fra_arena",
    "public_innsok_paa_felles_oppstart",
    "public_nav_bruker",
    "public_nav_enhet",
    "public_tiltakstype",
    "public_vedtak",
    "public_vurdering",
  ]

  distribusjon_view_ids = [
    "hendelse",
    "journaforing",
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
        { table = "arrangor" },
        { table = "deltaker" },
        { table = "deltaker_endring" },
        { table = "deltaker_status" },
        { table = "endring_fra_arrangor" },
        { table = "endring_fra_tiltakskoordinator" },
        { table = "forslag" },
        { table = "gjennomforing" },
        { table = "importert_fra_arena" },
        { table = "nav_bruker" },
        { table = "tiltakstype" },
        { table = "vedtak" },
      ]
    }
  ]
  postgresql_exclude_schemas = [
    {
      schema = "public"
      tables = [
        {
          table = "nav_bruker"
          columns = [
            "fornavn",
            "mellomnavn",
            "etternavn",
            "telefonnummer",
            "epost",
            "adresse",
            "er_skjermet",
          ]
        },
      ]
    }
  ]

  cloud_sql_proxy_vm_machine_type = "e2-micro"
  access_roles = concat(local.access_roles, [
    {
      role          = "READER"
      user_by_email = "bigqueryloader@teamoppfolging-prod-47fb.iam.gserviceaccount.com"
    },
    {
      role          = "READER"
      user_by_email = "rigmor.lindberg@nav.no"
    },
    {
      role          = "roles/bigquery.metadataViewer"
      user_by_email = "del_robert.graarup.kittilsen@nav.no"
    },
    {
      role          = "roles/bigquery.metadataViewer"
      user_by_email = "izis.froyhov@nav.no"
    },
    ], [
    for service_account in values(local.metabase_deltaker_view_readers) : {
      role          = "roles/bigquery.metadataViewer"
      user_by_email = service_account
    }
  ])
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
  postgresql_include_schemas = [
    {
      schema = "public"
      tables = [
        { table = "hendelse" },
        { table = "journalforingstatus" },
        { table = "tiltakshendelse" },
        { table = "varsel" },
      ]
    }
  ]
  postgresql_exclude_schemas = [
    {
      schema = "public"
      tables = [
        { table = "flyway_schema_history" },
        { table = "outbox_record" },
      ]
    }
  ]
  cloud_sql_proxy_vm_machine_type = "e2-micro"
  access_roles = concat(local.access_roles, [
    {
      role          = "roles/bigquery.metadataViewer"
      user_by_email = "izis.froyhov@nav.no"
    },
    ], [
    for service_account in values(local.metabase_distribusjon_view_readers) : {
      role          = "roles/bigquery.metadataViewer"
      user_by_email = service_account
    }
  ])
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
