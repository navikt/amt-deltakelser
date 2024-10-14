resource "google_bigquery_dataset" "amttiltak_dataset" {
  dataset_id    = "amttiltak_dataset"
  location      = var.gcp_project["region"]
  friendly_name = "amttiltak_dataset"
  labels        = {}

  access {
    role          = "OWNER"
    special_group = "projectOwners"
  }
  access {
    role          = "READER"
    special_group = "projectReaders"
  }
  access {
    role           = "READER"
    group_by_email = "all-users@nav.no"
  }
  access {
    role          = "WRITER"
    special_group = "projectWriters"
  }

  timeouts {}
}