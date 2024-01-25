resource "google_bigquery_dataset" "amttiltak_dataset" {
  dataset_id = "amttiltak_dataset"
  location   = var.gcp_project["region"]
  project    = var.gcp_project["project"]
  labels     = {}

  access {
    role          = "OWNER"
    special_group = "projectOwners"
  }
  access {
    role          = "READER"
    special_group = "projectReaders"
  }
  access {
    role          = "WRITER"
    special_group = "projectWriters"
  }
  timeouts {}
}

resource "google_datastream_connection_profile" "amttiltak_postgresql_connection_profile" {
  location              = var.gcp_project["region"]
  display_name          = "amttiltak-postgresql-connection-profile"
  connection_profile_id = "amttiltak-postgresql-connection-profile"

  postgresql_profile {
    hostname = google_compute_instance.amt_datastream_cloud_sql_proxy_vm.network_interface[0].network_ip
    port     = var.amttiltak_cloud_sql_port
    username = local.amttiltak_datastream_credentials["username"]
    password = local.amttiltak_datastream_credentials["password"]
    database = "amt-tiltak"
  }

  private_connectivity {
    private_connection = google_datastream_private_connection.amt_datastream_private_connection.id
  }
}

resource "google_datastream_stream" "amttiltak_datastream" {
  stream_id     = "amttiltak-datastream"
  display_name  = "amttiltak-datastream"
  desired_state = "RUNNING"
  project       = var.gcp_project["project"]
  location      = var.gcp_project["region"]
  labels        = {}
  backfill_all {}
  timeouts {}

  source_config {
    source_connection_profile = google_datastream_connection_profile.amttiltak_postgresql_connection_profile.id

    postgresql_source_config {
      max_concurrent_backfill_tasks = 0
      publication                   = "amttiltak_publication"
      replication_slot              = "amttiltak_replication"

      include_objects {
        postgresql_schemas {
          schema = "public"

          postgresql_tables {
            table = "deltaker_status"
          }

          postgresql_tables {
            table = "bruker"
            postgresql_columns {
              column = "id"
            }
            postgresql_columns {
              column = "person_ident"
            }
          }

          postgresql_tables {
            table = "deltaker"
            postgresql_columns {
              column = "id"
            }
            postgresql_columns {
              column = "bruker_id"
            }
            postgresql_columns {
              column = "gjennomforing_id"
            }
            postgresql_columns {
              column = "start_dato"
            }
            postgresql_columns {
              column = "slutt_dato"
            }
            postgresql_columns {
              column = "dager_per_uke"
            }
            postgresql_columns {
              column = "prosent_stilling"
            }
            postgresql_columns {
              column = "registrert_dato"
            }
            postgresql_columns {
              column = "created_at"
            }
            postgresql_columns {
              column = "modified_at"
            }
          }
        }
      }

      exclude_objects {
        postgresql_schemas {
          schema = "public"

          postgresql_tables {
            table = "bruker"
            postgresql_columns {
              column = "adressebeskyttelse"
            }
          }
        }
      }
    }
  }

  destination_config {
    destination_connection_profile = google_datastream_connection_profile.datastream_bigquery_connection_profile.id

    bigquery_destination_config {
      data_freshness = "3600s"

      single_target_dataset {
        dataset_id = "${var.gcp_project["project"]}:${google_bigquery_dataset.amttiltak_dataset.dataset_id}"
      }
    }
  }
}
