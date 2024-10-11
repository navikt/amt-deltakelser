resource "google_bigquery_dataset" "datastream_dataset" {
  dataset_id                 = local.dataset_id
  location                   = var.gcp_project["region"]
  project                    = var.gcp_project["project"]
  labels                     = {}
  delete_contents_on_destroy = var.big_query_dataset_delete_contents_on_destroy

  dynamic "access" {
    for_each = concat(local.default_access_roles, var.access_roles)
    content {
      role           = access.value.role
      special_group  = try(access.value.special_group, null)
      group_by_email = try(access.value.group_by_email, null)
      user_by_email  = try(access.value.user_by_email, null)
    }
  }

  dynamic "access" {
    for_each = var.authorized_views

    content {
      view {
        dataset_id = access.value.view.dataset_id
        project_id = access.value.view.project_id
        table_id   = access.value.view.table_id
      }
    }
  }

  timeouts {}
}

data "google_sql_database_instance" "database_instance" {
  name = var.cloud_sql_instance_name
}

// You can configure a virtual machine (VM) instance or an instance template to deploy and launch a Docker container.
// This Google-provided module handles the generation of metadata for deploying containers on GCE instances.
module "cloud_sql_auth_proxy_container_datastream" {
  // https://registry.terraform.io/modules/terraform-google-modules/container-vm/google/latest
  source  = "terraform-google-modules/container-vm/google"
  version = "3.1.1"
  // https://cloud.google.com/container-optimized-os/docs/release-notes
  cos_image_name = "cos-113-18244-85-5"
  container = {
    // https://github.com/GoogleCloudPlatform/cloud-sql-proxy/releases
    image   = "eu.gcr.io/cloudsql-docker/gce-proxy:1.35.1"
    command = ["/cloud_sql_proxy"]
    args = [
      "-instances=${data.google_sql_database_instance.database_instance.connection_name}=tcp:0.0.0.0:5432",
    ]
  }
  restart_policy = "Always"
}

resource "google_compute_instance" "compute_instance" {
  allow_stopping_for_update = true
  name                      = local.cloud_sql_proxy_vm_name
  machine_type              = var.cloud_sql_proxy_vm_machine_type
  project                   = var.gcp_project["project"]
  zone                      = var.gcp_project["zone"]

  boot_disk {
    initialize_params {
      image = module.cloud_sql_auth_proxy_container_datastream.source_image
    }
  }

  network_interface {
    network = var.datastream_vpc_resources.vpc_name
    // Ensures that a Private IP is assigned to the VM.
    access_config {}
  }

  // https://cloud.google.com/compute/docs/access/create-enable-service-accounts-for-instances
  service_account {
    scopes = ["cloud-platform"]
  }

  metadata = {
    gce-container-declaration = module.cloud_sql_auth_proxy_container_datastream.metadata_value
  }

  labels = {
    container-vm = module.cloud_sql_auth_proxy_container_datastream.vm_container_label
  }
}

resource "google_datastream_connection_profile" "postgresql_connection_profile" {
  location              = var.gcp_project["region"]
  display_name          = local.postgres_connection_profile_id
  connection_profile_id = local.postgres_connection_profile_id

  postgresql_profile {
    hostname = google_compute_instance.compute_instance.network_interface[0].network_ip
    port     = 5432
    username = var.cloud_sql_instance_db_credentials["username"]
    password = var.cloud_sql_instance_db_credentials["password"]
    database = var.cloud_sql_instance_db_name
  }

  private_connectivity {
    private_connection = var.datastream_vpc_resources.private_connection_id
  }
}

resource "google_datastream_stream" "datastream" {
  stream_id     = local.datastream_id
  display_name  = local.datastream_id
  desired_state = var.datastream_desired_state
  project       = var.gcp_project["project"]
  location      = var.gcp_project["region"]
  labels        = {}
  backfill_all {}
  timeouts {}

  source_config {
    source_connection_profile = google_datastream_connection_profile.postgresql_connection_profile.id

    postgresql_source_config {
      max_concurrent_backfill_tasks = 0
      publication                   = local.cloud_sql_instance_publication_name
      replication_slot              = local.cloud_sql_instance_replication_name

      exclude_objects {

        dynamic "postgresql_schemas" {
          for_each = var.postgresql_exclude_schemas
          content {
            schema = postgresql_schemas.value.schema

            dynamic "postgresql_tables" {
              for_each = coalesce(postgresql_schemas.value.tables, [])
              content {
                table = postgresql_tables.value.table

                dynamic "postgresql_columns" {
                  for_each = coalesce(postgresql_tables.value.columns, [])
                  content {
                    column = postgresql_columns.value
                  }
                }
              }
            }
          }
        }
      }

      include_objects {

        dynamic "postgresql_schemas" {
          for_each = var.postgresql_include_schemas
          content {
            schema = postgresql_schemas.value.schema

            dynamic "postgresql_tables" {
              for_each = coalesce(postgresql_schemas.value.tables, [])
              content {
                table = postgresql_tables.value.table

                dynamic "postgresql_columns" {
                  for_each = coalesce(postgresql_tables.value.columns, [])
                  content {
                    column = postgresql_columns.value
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  destination_config {
    destination_connection_profile = var.datastream_vpc_resources.bigquery_connection_profile_id

    bigquery_destination_config {
      data_freshness = var.bigquery_table_freshness

      single_target_dataset {
        dataset_id = "${var.gcp_project["project"]}:${google_bigquery_dataset.datastream_dataset.dataset_id}"
      }
    }
  }
}